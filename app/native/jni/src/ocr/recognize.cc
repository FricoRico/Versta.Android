//
// OCR recognition: charset loading, the 48px-high strip normalization,
// whitespace splitting, batched CTC decode with per-char positions, the
// recognizer-confidence orientation duel and PULC script routing.
//
// Sources (translator-ocr/ppocr.rs): extract_script_lines, split_at_whitespace,
// guess_canonical_orientation_and_assign_quadrants,
// classify_and_assign_recognizers.
//

#define LOG_TAG "VerstaOcr"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <numeric>
#include <thread>
#include <unordered_map>

#include "include/Log.h"
#include "include/ocr_pipeline.h"
#include "include/parallel.h"

namespace ocr {

// ---------------------------------------------------------------------------
// Charset
// ---------------------------------------------------------------------------

std::vector<char32_t> loadCharset(const std::string& path) {
    // Decode UTF-8 lines; Jieba TSVs (key \t freq) truncate at the tab.
    std::ifstream stream(path);
    if (!stream) {
        LOGE("OCR: cannot open charset %s", path.c_str());
        return {U'x'};
    }
    std::vector<char32_t> charset;
    std::string line;
    while (std::getline(stream, line)) {
        const auto tab = line.find('\t');
        const std::string key = tab == std::string::npos ? line : line.substr(0, tab);
        std::u32string decoded;
        for (size_t i = 0; i < key.size();) {
            const auto byte = static_cast<unsigned char>(key[i]);
            char32_t ch;
            size_t len;
            if (byte < 0x80) { ch = byte; len = 1; }
            else if ((byte & 0xE0) == 0xC0) { ch = byte & 0x1F; len = 2; }
            else if ((byte & 0xF0) == 0xE0) { ch = byte & 0x0F; len = 3; }
            else if ((byte & 0xF8) == 0xF0) { ch = byte & 0x07; len = 4; }
            else { ch = 0xFFFD; len = 1; }
            if (i + len > key.size()) break;
            for (size_t k = 1; k < len; k++) {
                ch = (ch << 6) | (static_cast<unsigned char>(key[i + k]) & 0x3F);
            }
            decoded += ch;
            i += len;
        }
        // "space" token words are not used in PP-OCR vocabs; empty decode = space.
        charset.push_back(decoded.empty() ? U' ' : decoded[0]);
    }
    if (charset.empty()) charset.push_back(U'x');
    // PP-OCR models emit vocab+2 classes: blank (0), dict chars, and a trailing
    // space class. Without this entry the decode's range guard silently drops
    // every space (reference: load_charset wraps leading blank + trailing pad).
    charset.push_back(U' ');
    return charset;
}

// ---------------------------------------------------------------------------
// Strip preprocessing
// ---------------------------------------------------------------------------

/// uint8 -> (v/255 - 0.5)/0.5 per channel lookup (reference: REC_NORM_LUT).
static float recLut[256];
static bool recLutInitialized = [] {
    for (int v = 0; v < 256; v++) recLut[v] = (v / 255.0f - 0.5f) / 0.5f;
    return true;
}();

/// Fills a [3 x H x wStride] NCHW buffer from the resized strip with the
/// LUT-normalized channels; any wStride slack right of the content stays 0.
static std::vector<float> nchwFill(const cv::Mat& resized, int wStride) {
    std::vector<float> buffer(3 * resized.rows * wStride, 0.0f);
    const int plane = resized.rows * wStride;
    for (int y = 0; y < resized.rows; y++) {
        const auto* row = resized.ptr<cv::Vec3b>(y);
        const int base = y * wStride;
        for (int x = 0; x < resized.cols; x++) {
            const int idx = base + x;
            buffer[idx] = recLut[row[x][0]];
            buffer[plane + idx] = recLut[row[x][1]];
            buffer[2 * plane + idx] = recLut[row[x][2]];
        }
    }
    return buffer;
}

/// Resizes to the natural 48-high width, then right-pads into the bucket-wide
/// buffer (reference pads rather than stretches, keeping glyph scale intact;
/// firing fractions rescale by wExact/wUsed downstream).
static std::vector<float> nchwPadded(const cv::Mat& strip, int targetH, int wExact, int wUsed) {
    cv::Mat resized;
    cv::resize(strip, resized, {wExact, targetH}, 0, 0, cv::INTER_AREA);
    return nchwFill(resized, wUsed);
}

/// Fixed-size stretch variant for classifiers with a static input contract.
static std::vector<float> nchw(const cv::Mat& strip, int targetH, int targetW) {
    cv::Mat resized;
    cv::resize(strip, resized, {targetW, targetH}, 0, 0, cv::INTER_AREA);
    return nchwFill(resized, targetW);
}

// ---------------------------------------------------------------------------
// CTC decode
// ---------------------------------------------------------------------------

static bool isPunct(char32_t c) {
    return (c >= 0x21 && c <= 0x2F) || (c >= 0x3A && c <= 0x40) ||
           (c >= 0x5B && c <= 0x60) || (c >= 0x7B && c <= 0x7E) ||
           (c >= 0x2000 && c <= 0x206F) || c == 0x3001 || c == 0x3002;
}

/// Greedy CTC over one strip: char = argmax at its firing column; firings
/// record (char, score, native-column) with the min-confidence gate
/// (punctuation-sensitive). Vertical strips arrive pre-rotated (rot90
/// upstream), so decode() always sees horizontal content.
static Decoded decode(const float* logits, int columns, int classes,
                       const std::vector<char32_t>& charset,
                       float contentFraction, float minScore) {
    Decoded decoded;
    if (classes <= 0) return decoded;

    // PP-OCR CTC convention: class 0 is blank, dict chars start at class 1.
    const int blank = 0;

    float scoreSum = 0;
    int count = 0;
    int prev = blank;
    bool separator = true;
    for (int x = 0; x < columns; x++) {
        const float* step = logits + static_cast<size_t>(x) * classes;
        int argmax = 0;
        float maxV = step[0];
        for (int c = 1; c < classes; c++) {
            if (step[c] > maxV) { maxV = step[c]; argmax = c; }
        }
        if (argmax == blank) {
            // Blank separates runs: the same char fired again after a blank is
            // a genuine repeat ("tt", "11"), not a duplicate to collapse.
            separator = true;
            continue;
        }
        if (!separator && argmax == prev) continue;
        separator = false;
        prev = argmax;

        const int token = argmax - 1;
        if (token >= (int)charset.size()) continue;
        // Chars below the gate drop entirely, keeping firings 1:1 with text;
        // punctuation keeps its own (lower) floor.
        if (maxV < minScore && !(isPunct(charset[token]) && maxV >= REC_PUNCT_MIN_SCORE)) continue;
        // seq columns span the padded input; rescale onto the content axis
        // (reference: at = (t+0.5)/seq_len / content_fraction, clamped).
        const float at = std::min((x + 0.5f) / (static_cast<float>(columns) * contentFraction), 1.0f);
        decoded.firings.push_back({charset[token], maxV, at});
        decoded.text += charset[token];
        scoreSum += maxV;
        count++;
    }
    decoded.score = count > 0 ? scoreSum / count : 0;
    decoded.accepted = count;
    return decoded;
}

// ---------------------------------------------------------------------------
// Whitespace splitting (reference: split_at_whitespace)
// ---------------------------------------------------------------------------

static std::vector<int> whitespaceCuts(const cv::Mat& strip) {
    const int w = strip.cols, h = strip.rows;

    // Background estimate: median column luminance.
    cv::Mat gray;
    cv::cvtColor(strip, gray, cv::COLOR_RGB2GRAY);
    std::vector<uint8_t> flat(gray.datastart, gray.dataend);
    std::nth_element(flat.begin(), flat.begin() + flat.size() / 2, flat.end());
    const float bg = flat[flat.size() / 2];

    const float cut = std::max(bg - 25.0f, 10.0f); // darker ink on lighter bg
    const float cutInv = std::min(bg + 25.0f, 245.0f);

    std::vector<float> density(w, 0);
    for (int x = 0; x < w; x++) {
        int ink = 0;
        for (int y = 0; y < h; y++) {
            const auto l = gray.at<uint8_t>(y, x);
            if (l < cut || l > cutInv) ink++;
        }
        density[x] = static_cast<float>(ink) / h;
    }

    // Whitespace: runs of near-empty columns, sized relative to the strip
    // height — fixed px gaps explode thin noisy strips into slivers.
    const int minGap = std::max(4, h / 3);
    const int minSpan = std::max(8, h);

    std::vector<int> cuts;
    int start = -1;
    int lastCut = 0;
    for (int x = 0; x < w; x++) {
        if (density[x] <= 0.008f) {
            if (start < 0) start = x;
        } else if (start >= 0) {
            if (x - start >= minGap && (start + x) / 2 - lastCut >= minSpan) {
                cuts.push_back((start + x) / 2);
                lastCut = (start + x) / 2;
            }
            start = -1;
        }
    }
    return cuts;
}

/// Whitespace margins off a decode, text kept 1:1 with firings. False when
/// the whole decode is whitespace.
static bool trimDecoded(const Decoded& decoded, Decoded& out) {
    const size_t firstCh = decoded.text.find_first_not_of(U' ');
    if (firstCh == std::u32string::npos) return false;
    auto first = decoded.firings.begin();
    while (first != decoded.firings.end() && first->ch == U' ') ++first;
    auto last = decoded.firings.end();
    while (last != first && (last - 1)->ch == U' ') --last;
    out.text = decoded.text.substr(firstCh, decoded.text.find_last_not_of(U' ') - firstCh + 1);
    out.firings.assign(first, last);
    out.score = decoded.score;
    out.accepted = decoded.accepted;
    return true;
}

// ---------------------------------------------------------------------------
// Strip recognition (parallel across REC_PARALLELISM sessions)
// ---------------------------------------------------------------------------

/// 48-high natural width snapped to the recognizer's 32-wide buckets
/// (4096 = OOM cap; whitespace splitting keeps spaced lines far below).
static int bucketizeRunWidth(int wExact) {
    return std::clamp((wExact + REC_WIDTH_BUCKET - 1) / REC_WIDTH_BUCKET * REC_WIDTH_BUCKET,
                      REC_WIDTH_BUCKET, 4096);
}

/// Bucketed run width for a strip presented rotated to (w, h) — the width
/// contract the recognizer was trained around (48-row scale, 32-wide buckets).
static int recBucketedWidth(int w, int h) {
    return bucketizeRunWidth(std::max(1, static_cast<int>(
        std::lround(w * (REC_TARGET_HEIGHT / (float)std::max(h, 1))))));
}

static Decoded runStrip(Recognizer* rec, Model::Session* session, const cv::Mat& strip) {
    const int wExact = std::max(1, static_cast<int>(
        std::lround(strip.cols * (REC_TARGET_HEIGHT / (float)strip.rows))));
    const int width = bucketizeRunWidth(wExact);
    const float contentFraction = std::min(wExact / static_cast<float>(width), 1.0f);
    auto input = nchwPadded(strip, REC_TARGET_HEIGHT, std::min(wExact, width), width);

    std::vector<int> outShape;
    auto out = rec->model->run(session->session, input,
                               {1, 3, REC_TARGET_HEIGHT, width}, outShape);
    const int classes = outShape.back();
    const int columns = outShape.size() >= 2
        ? outShape[outShape.size() - 2]
        : static_cast<int>(out.size()) / classes;
    return decode(out.data(), columns, classes, rec->charset, contentFraction, REC_MIN_SCORE);
}

// ---------------------------------------------------------------------------
// Strip cache (one dewarp per box per pass)
// ---------------------------------------------------------------------------

std::vector<DewarpedStrip> Engine::buildStrips(const cv::Mat& image,
                                               const std::vector<TextBox>& boxes) {
    const float thicknessPad = detPoolCompensationPx();
    std::vector<DewarpedStrip> strips(boxes.size());

    parallelFor(std::min<size_t>(REC_PARALLELISM, std::max<size_t>(1, boxes.size())), boxes.size(),
                [&](size_t, size_t i) {
        cv::Mat strip;
        OrientedRect region;
        dewarpLine(image, boxes[i], thicknessPad, strip, &region);
        strips[i] = {std::move(strip), region};
    });
    return strips;
}

/// Splits and recognizes one box's cached strip in its reading quadrant
/// (canonical). Returns lines owning the box's text content.
static std::vector<TextLine> recognizeBox(const TextBox& box, const DewarpedStrip& dewarped,
                                          Recognizer* rec, size_t sessionIdx,
                                          int canonical, Profile profile, size_t boxIdx) {
    std::vector<TextLine> lines;
    if (!rec) return lines;

    if (dewarped.strip.empty()) return lines;
    cv::Mat strip = rot90(dewarped.strip, stripRotation(box, canonical));

    // Split long lines at whitespace gaps (cheap for narrow strips).
    std::vector<std::pair<int, int>> spans;
    if (strip.cols > REC_WHITESPACE_SPLIT_MAX_WIDTH && strip.cols > 2 * strip.rows) {
        const int minSpan = std::max(8, strip.rows);
        int prev = 0;
        for (int cut : whitespaceCuts(strip)) {
            if (cut - prev >= minSpan) spans.emplace_back(prev, cut);
            prev = cut;
        }
        if (strip.cols - prev >= minSpan) spans.emplace_back(prev, strip.cols);
    }
    if (spans.empty()) spans.emplace_back(0, strip.cols);

    auto* session = rec->sessions[sessionIdx % rec->sessions.size()].get();
    const float dropScore = profile == Profile::Live ? LIVE_REC_DROP_SCORE : REC_DROP_SCORE;

    // One TextLine per box: whitespace-split spans are recognized separately
    // (rec-width limit) and stitched back with a ' ' join plus an injected
    // space firing at each boundary — the reference's stitch_chunk_firings.
    TextLine line;
    line.box = box;
    float scoreSum = 0;
    int scoreCount = 0;
    bool anySpan = false;
    for (const auto& [x0, x1] : spans) {
        const cv::Mat span = strip(cv::Rect(x0, 0, x1 - x0, strip.rows)).clone();
        auto decoded = runStrip(rec, session, span);

        // Trim leading/trailing whitespace firings (mirrors the text trim), so
        // the span contributes exactly its content and joins supply the spaces.
        Decoded trimmed;
        if (!trimDecoded(decoded, trimmed)) continue;

        if (!line.text.empty()) {
            line.text += U' ';
            // Injected space firing at the following span's start fraction.
            line.firings.push_back({U' ', 1.0f, x0 / static_cast<float>(strip.cols)});
        }
        line.text += trimmed.text;
        scoreSum += decoded.score * decoded.accepted;
        scoreCount += decoded.accepted;
        anySpan = true;

        for (auto& f : trimmed.firings) {
            CharFiring adjusted = f;
            adjusted.at = (x0 + f.at * (x1 - x0)) / static_cast<float>(strip.cols);
            line.firings.push_back(adjusted);
        }
    }
    if (!anySpan) return lines;
    line.score = scoreCount > 0 ? scoreSum / scoreCount : 0;
    if (line.score < dropScore) return lines;
    line.stripIdx = static_cast<int>(boxIdx);
    lines.push_back(std::move(line));
    return lines;
}

/// Builds a TextLine from a cached duel decode — the single-span case of
/// recognizeBox verbatim: the strip ran unsplit (x0=0, so firing fractions
/// stand), space firings only exist between spans, and the score is the
/// decoded mean gated by the profile's drop threshold.
static bool lineFromDuel(const TextBox& box, const Decoded& decoded, float dropScore,
                         TextLine& out) {
    Decoded trimmed;
    if (!trimDecoded(decoded, trimmed)) return false;

    out.box = box;
    out.text = std::move(trimmed.text);
    out.firings = std::move(trimmed.firings);
    out.score = decoded.score;
    return out.score >= dropScore;
}

// ---------------------------------------------------------------------------
// Orientation duel (reference: guess_canonical_orientation_and_assign_quadrants)
// ---------------------------------------------------------------------------

int Engine::canonicalQuadrant(std::vector<TextBox>& boxes,
                              const std::vector<DewarpedStrip>& strips,
                              Recognizer* duel) {
    _duelCache.clear();
    _duelKey.clear();
    const bool vertical = std::any_of(boxes.begin(), boxes.end(), [](const TextBox& b) {
        return b.aabb.height > 2 * b.aabb.width;
    });
    const int rots[] = {0, 2, 1, 3};
    const int nRots = vertical ? 4 : 2;
    if (boxes.empty() || !duel) return 0;

    // Duel on the highest-confidence boxes only; the modal reading quadrant
    // needs just a few confident samples (regular pages or live frames alike).
    std::vector<size_t> duelBoxes(boxes.size());
    std::iota(duelBoxes.begin(), duelBoxes.end(), 0);
    if (duelBoxes.size() > DUEL_MAX_BOXES) {
        std::sort(duelBoxes.begin(), duelBoxes.end(),
                  [&](size_t a, size_t b) { return boxes[a].score > boxes[b].score; });
        duelBoxes.resize(DUEL_MAX_BOXES);
    }

    // Duel runs: (box, candidate) pairs over the session pool in parallel —
    // sequentially this dominates the whole frame on 20+ boxes. Every decode
    // is kept: the winner-rotation entries become recognize()'s cache.
    const size_t nBoxes = boxes.size();
    std::vector<Decoded> decodes(static_cast<size_t>(nRots) * nBoxes);
    std::vector<int> decRots(static_cast<size_t>(nRots) * nBoxes * 2, 0); // (cols,rows) post-rot
    std::vector<double> totals(nRots, 0.0);
    std::vector<int> counts(nRots, 0);
    {
        struct Task { size_t boxIdx; int r; };
        // Sort by post-rotation run width: MNN skips a session's resize
        // rebuild when consecutive shapes match, so same-width neighbours on
        // the same session only rebuild once per distinct width.
        auto runWidthOf = [&](const Task& t) {
            const auto& s = strips[t.boxIdx].strip;
            const int rot = stripRotation(boxes[t.boxIdx], rots[t.r]);
            return recBucketedWidth(rot % 2 == 0 ? s.cols : s.rows,
                                    rot % 2 == 0 ? s.rows : s.cols);
        };
        auto byWidth = [&](const Task& a, const Task& b) {
            return runWidthOf(a) < runWidthOf(b);
        };
        std::vector<Task> tasks;
        for (size_t i : duelBoxes) {
            if (!strips[i].strip.empty()) tasks.push_back({i, 0});
        }
        std::sort(tasks.begin(), tasks.end(), byWidth);

        std::mutex scoreMu;
        auto runPool = [&](std::vector<Task>& pending) {
            parallelFor(std::min<size_t>(duel->sessions.size(), pending.size()), pending.size(),
                        [&](size_t wi, size_t t) {
                auto* session = duel->sessions[wi].get();
                const auto& task = pending[t];
                const cv::Mat strip = rot90(strips[task.boxIdx].strip, stripRotation(boxes[task.boxIdx], rots[task.r]));
                auto decoded = runStrip(duel, session, strip);
                const size_t slot = static_cast<size_t>(task.r) * nBoxes + task.boxIdx;
                decodes[slot] = decoded;
                decRots[slot * 2] = strip.cols;
                decRots[slot * 2 + 1] = strip.rows;
                if (decoded.text.empty()) return;
                const int density = static_cast<int>(decoded.text.size()) + 1;
                const double weight = density > 2 ? decoded.score : decoded.score / 2.0;
                std::lock_guard<std::mutex> lock(scoreMu);
                totals[task.r] += weight;
                counts[task.r]++;
            });
        };
        runPool(tasks);

        // Decisive r0 skips the remaining rotations: a page read upright by
        // the recognizer (high mean over a broad sweep of sampled boxes) never
        // flips on the hedging candidates — and the duel costs more than the
        // duel is worth otherwise. Weak r0 (upside-down page, wrong quadrant,
        // exotic script) still buys the full candidate set.
        const size_t attempted = tasks.size();
        const bool decisive = attempted > 0 && counts[0] * 4 >= attempted * 3
                              && totals[0] / std::max(counts[0], 1) >= DUEL_DECISIVE_SCORE;
        if (!decisive) {
            std::vector<Task> pending;
            for (int r = 1; r < nRots; r++) {
                for (size_t i : duelBoxes) {
                    if (!strips[i].strip.empty()) pending.push_back({i, r});
                }
            }
            std::sort(pending.begin(), pending.end(), byWidth);
            runPool(pending);
        }
    }

    double bestScore = -1e9;
    int bestIdx = 0;
    int best = 0;
    for (int r = 0; r < nRots; r++) {
        if (counts[r] == 0) continue;
        // Prior at zero evidence; a small tax against hedging rotations.
        const double mean = totals[r] / counts[r];
        const double score = mean - (rots[r] == 0 ? 0.0 : 0.0105);
        if (score > bestScore) {
            bestScore = score;
            bestIdx = r;
            best = rots[r];
        }
    }

    // Winner decodes feed the recognize cache: same strip, same quadrant
    // rotation, same recognizer — recognition becomes free for these boxes.
    _duelCache.assign(nBoxes, {});
    for (size_t i : duelBoxes) {
        const size_t slot = static_cast<size_t>(bestIdx) * nBoxes + i;
        if (decodes[slot].accepted <= 0) continue;
        _duelCache[i].decoded = std::move(decodes[slot]);
        _duelCache[i].rotCols = decRots[slot * 2];
        _duelCache[i].rotRows = decRots[slot * 2 + 1];
        _duelCache[i].valid = true;
    }
    _duelKey.clear();
    for (const auto& slot : _slots) {
        if (slot->recognizer.get() == duel) { _duelKey = slot->key; break; }
    }

    // Reading direction per box, snapped onto the canonical quadrant grid.
    for (auto& box : boxes) {
        const float axisDeg = box.tight.angle * 180.0f / static_cast<float>(CV_PI);
        const int q = stripRotation(box, best);
        box.referenceAngle = (axisDeg + q * 90.0f) * static_cast<float>(CV_PI) / 180.0f;

        // Emit corners in reading order when the strip flipped.
        if (q != 0) {
            Point corners[4];
            for (int i = 0; i < 4; i++) corners[i] = box.corners[(i - q + 4) % 4];
            for (int i = 0; i < 4; i++) box.corners[i] = corners[i];
        }
    }

    return best;
}

// ---------------------------------------------------------------------------
// PULC script routing (reference: classify_and_assign_recognizers)
// ---------------------------------------------------------------------------

std::vector<std::string> Engine::routeScripts(const cv::Mat& upright,
                                              const std::vector<TextBox>& boxes,
                                              const std::vector<DewarpedStrip>& strips,
                                              int canonical) {
    std::vector<std::string> keys(boxes.size(), _dominantKey);
    if (!_pul || _pulSessions.empty() || _routes.empty()) {
        return keys;
    }

    std::vector<int> histogram(PULC_CLASSES, 0);
    std::vector<int> classes(boxes.size(), -1);
    const float minArea = PULC_MIN_STRIP_AREA;

    const size_t workers = std::min<size_t>(_pulSessions.size(), boxes.size());
    std::vector<std::vector<int>> localHistograms(workers, std::vector<int>(PULC_CLASSES, 0));
    parallelFor(workers, boxes.size(), [&](size_t wi, size_t i) {
        auto* session = _pulSessions[wi].get();
        const auto& b = boxes[i];
        const int w = b.aabb.width, h = b.aabb.height;
        if (w < PULC_MIN_STRIP_WIDTH || h < PULC_MIN_STRIP_HEIGHT) return;
        if (static_cast<float>(w) * h < minArea) return;
        if (static_cast<float>(w) * h < PULC_MIN_IMAGE_AREA_RATIO * upright.cols * upright.rows) return;
        if (strips[i].strip.empty()) return;

        const cv::Mat strip = rot90(strips[i].strip, stripRotation(b, canonical));

        auto input = nchw(strip, PULC_HEIGHT, PULC_WIDTH);
        std::vector<int> shape;
        auto out = _pul->run(session->session, input,
                             {1, 3, PULC_HEIGHT, PULC_WIDTH}, shape);
        if (out.empty()) return;
        const int argmax = static_cast<int>(std::max_element(out.begin(), out.end()) - out.begin());
        if (out[argmax] < PULC_MIN_SCORE) return;
        if (argmax < 0 || argmax >= (int)_routes.size()) return;
        classes[i] = argmax;
        localHistograms[wi][argmax]++;
    });
    for (const auto& local : localHistograms) {
        for (int c = 0; c < PULC_CLASSES; c++) histogram[c] += local[c];
    }

    // Dominant script = most common *non-latin* class; latin merges into it.
    int dominantClass = -1, dominantCount = 0;
    for (int c = 0; c < PULC_CLASSES; c++) {
        if (c == 9) continue; // latin
        if (histogram[c] > dominantCount) { dominantCount = histogram[c]; dominantClass = c; }
    }

    for (size_t i = 0; i < boxes.size(); i++) {
        const int c = classes[i];
        if (c < 0) continue; // ungated boxes keep the dominant key
        const std::string& route = _routes[c];
        if (route.empty()) continue; // script has no recognizer in this bundle
        if (c == 9) continue; // latin keeps the dominant key
        keys[i] = route;
    }

    // Minority fold: isolated script islands under the dominant script.
    if (dominantClass >= 0 && dominantClass < (int)_routes.size() && !_routes[dominantClass].empty()) {
        std::unordered_map<std::string, int> runs;
        for (const auto& key : keys) runs[key]++;
        for (auto& key : keys) {
            if (key == _routes[dominantClass]) continue;
            if (runs[key] < 2) key = _routes[dominantClass];
        }
    }

    return keys;
}

// ---------------------------------------------------------------------------
// Full recognize pass
// ---------------------------------------------------------------------------

std::vector<TextLine> Engine::recognize(std::vector<TextBox>& boxes,
                                        const std::vector<DewarpedStrip>& strips,
                                        const std::vector<std::string>& keys, int canonical,
                                        Profile profile) {
    std::vector<TextLine> lines;
    lines.reserve(boxes.size());

    // Order by recognize-run width so identical input shapes land back-to-back
    // on each session (MNN then skips the per-run resize rebuild).
    auto recWidthOf = [&](size_t i) {
        if (i < strips.size() && !strips[i].strip.empty()) {
            const int rot = stripRotation(boxes[i], canonical);
            const auto& s = strips[i].strip;
            return recBucketedWidth(rot % 2 == 0 ? s.cols : s.rows,
                                    rot % 2 == 0 ? s.rows : s.cols);
        }
        return 4096; // strip-less fallbacks run last
    };
    std::vector<size_t> order(boxes.size());
    std::iota(order.begin(), order.end(), 0);
    std::sort(order.begin(), order.end(), [&](size_t a, size_t b) {
        return recWidthOf(a) < recWidthOf(b);
    });

    std::mutex outMu;
    parallelFor(std::min<size_t>(REC_PARALLELISM, std::max<size_t>(1, boxes.size())), order.size(),
                [&](size_t wi, size_t ord) {
        const size_t i = order[ord];
        Recognizer* rec = recognizer(keys[i]);
        if (!rec) return;

        TextLine line;
        bool have = false;
        // Duel cache hit: the same strip already ran at the same
        // rotation on this recognizer. Skipped when a whitespace split
        // would have re-cut the strip differently.
        if (i < _duelCache.size() && keys[i] == _duelKey) {
            const auto& entry = _duelCache[i];
            const bool wouldSplit = entry.rotCols > REC_WHITESPACE_SPLIT_MAX_WIDTH
                                 && entry.rotCols > 2 * entry.rotRows;
            const float dropScore = profile == Profile::Live
                ? LIVE_REC_DROP_SCORE : REC_DROP_SCORE;
            if (entry.valid && !wouldSplit) {
                have = lineFromDuel(boxes[i], entry.decoded, dropScore, line);
                if (have) {
                    line.stripIdx = static_cast<int>(i);
                }
            }
        }
        if (!have) {
            auto found = recognizeBox(boxes[i], strips[i], rec, wi, canonical, profile, i);
            if (found.empty()) return;
            line = std::move(found.front());
        }

        line.text = repairCyrillicWordMixing(line.text);
        std::lock_guard<std::mutex> lock(outMu);
        lines.push_back(std::move(line));
    });

    return lines;
}

// ---------------------------------------------------------------------------
// Cyrillic confusable repair (reference: repair_cyrillic_word_mixing)
// ---------------------------------------------------------------------------

std::u32string repairCyrillicWordMixing(const std::u32string& text) {
    // Latin visually-identical equivalents snapped back inside otherwise-Cyrillic
    // words — v5 cyrillic model merges the alphabets and the recognizer fires
    // Latin keys on Cyrillic glyphs.
    static const std::unordered_map<char32_t, char32_t> latinToCyrillic = {
        {U'A', 0x0410}, {U'B', 0x0412}, {U'C', 0x0421}, {U'E', 0x0415},
        {U'H', 0x041D}, {U'K', 0x041A}, {U'M', 0x041C}, {U'O', 0x041E},
        {U'P', 0x0420}, {U'T', 0x0422}, {U'X', 0x0425},
        {U'a', 0x0430}, {U'c', 0x0441}, {U'e', 0x0435}, {U'o', 0x043E},
        {U'p', 0x0440}, {U'x', 0x0445}, {U'y', 0x0443},
    };
    auto isCyrillic = [](char32_t c) { return c >= 0x0400 && c <= 0x04FF; };
    auto isLatin = [](char32_t c) {
        return (c >= U'A' && c <= U'Z') || (c >= U'a' && c <= U'z');
    };

    std::u32string out = text;
    for (size_t i = 0; i < out.size(); i++) {
        if (!isLatin(out[i])) continue;
        auto it = latinToCyrillic.find(out[i]);
        if (it == latinToCyrillic.end()) continue;
        const bool leftCyr = i > 0 && isCyrillic(out[i - 1]);
        const bool rightCyr = i + 1 < out.size() && isCyrillic(out[i + 1]);
        if (leftCyr || rightCyr) out[i] = it->second;
    }
    return out;
}

} // namespace ocr
