#pragma once

//
// PP-OCR pipeline over MNN — types and engine interface shared across the
// ocr_*.cc component files. Pipeline constants mirror the reference
// implementation (translator-ocr/ppocr.rs); each is annotated at its use site.
//

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

#include <MNN/Interpreter.hpp>
#include <opencv2/opencv.hpp>

namespace ocr {

constexpr float DET_SCORE_THRESHOLD = 0.3f;
constexpr float DET_BOX_MIN_SCORE = 0.6f;
constexpr int DET_MIN_AREA = 64;
constexpr float DET_UNCLIP_RATIO = 1.2f;
constexpr int DET_BOX_BORDER = 4;
constexpr int DET_MAX_SIDE = 4096;
constexpr int DET_LIVE_PIXEL_BUDGET = 650000;

constexpr float LIVE_DET_BOX_MIN_SCORE = 0.68f;
constexpr int LIVE_DET_MIN_AREA = 350;

constexpr float REC_MIN_SCORE = 0.3f;
constexpr float REC_PUNCT_MIN_SCORE = 0.3f;
constexpr float REC_DROP_SCORE = 0.5f;
constexpr float LIVE_REC_DROP_SCORE = 0.65f;
constexpr int REC_TARGET_HEIGHT = 48;
constexpr int REC_WIDTH_BUCKET = 32;
constexpr int REC_WHITESPACE_SPLIT_MAX_WIDTH = 960;
constexpr int REC_PARALLELISM = 4;
// Orientation duel samples the most confident boxes only — the modal reading
// quadrant needs a handful of confident votes, not every box on the page. A
// decisive rotation-0 sweep skips the remaining candidates entirely.
constexpr int DUEL_MAX_BOXES = 8;
constexpr double DUEL_DECISIVE_SCORE = 0.75;

constexpr int PULC_WIDTH = 160;
constexpr int PULC_HEIGHT = 80;
constexpr int PULC_CLASSES = 10;      // fixed output classes of the PULC script classifier
constexpr float PULC_MIN_SCORE = 0.85f;
constexpr int PULC_MIN_STRIP_AREA = 768;
constexpr int PULC_MIN_STRIP_WIDTH = 24;
constexpr int PULC_MIN_STRIP_HEIGHT = 8;
constexpr float PULC_MIN_IMAGE_AREA_RATIO = 0.00030f;

// translator-raster/text_metrics.rs
constexpr float MIN_LINE_ASPECT = 3.0f;
constexpr float TRUSTED_LINE_ASPECT = 30.0f;
constexpr float TRUSTED_TILT_DELTA = 0.1745329f; // pi / 18
constexpr float TILT_KEEP_EVIDENCE = 0.08f;
constexpr int INK_BOLD_MIN_PX = 30;
constexpr float MODEL_BOLD_THRESHOLD = 0.65f;
constexpr int MATTE_MAX_WIDTH = 512;      // matte conv budget cap (sessW)
constexpr uint8_t INK_CUT = 40;          // matte alpha at/above which a texel counts as ink

// translator-ocr/ppocr.rs contour_strip_warp: strip band = p05-p95 contour
// spread about the spine, inflated 2.4x (glyph margin + whitespace the
// recognizer expects) plus a descender slice below the baseline.
constexpr float STRIP_BAND_INFLATE = 2.4f;
constexpr float STRIP_DESCENDER_VPAD_FRAC = 0.4f; // descender slice, as band fraction
constexpr int STRIP_MIN_HEIGHT = 8;
constexpr int STRIP_MAX_HEIGHT = 256;
constexpr int STRIP_MIN_WIDTH = 16;
constexpr int STRIP_MAX_WIDTH = 4096;

constexpr float PPOCR_DET_MEAN[3] = {0.485f, 0.456f, 0.406f};
constexpr float PPOCR_DET_STD[3] = {0.229f, 0.224f, 0.225f};

enum class Profile { Still = 0, Live = 1 };

struct Point {
    float x, y;
};

struct OrientedRect {
    float cx = 0, cy = 0, width = 0, height = 0, angle = 0; // rad; width along reading axis
};

/// UTF-32 -> UTF-8 (implemented in engine.cc).
std::string utf8For(const std::u32string& text);

/// One detected text box, in upright-image pixel space.
struct TextBox {
    cv::Rect aabb;                    // axis-aligned fallback region
    Point corners[4];                 // oriented quad, reading order
    OrientedRect oriented;            // inflated oriented rect
    OrientedRect tight;               // glyph-band oriented rect
    float tiltAngle = 0;              // final line angle in the box's reading quadrant
    float referenceAngle = 0;         // final absolute reading angle (quadrant + tilt)
    std::vector<Point> contour;       // detection contour, flat
    float score = 0;
};

struct CharFiring {
    char32_t ch;
    float score;
    float at; // 0..1 along the reading axis
};

/// CTC decode of one strip: text, per-char firings and the mean confidence.
struct Decoded {
    std::u32string text;
    std::vector<CharFiring> firings;
    float score = 0;
    int accepted = 0; // chars that cleared the confidence gate
};

/// One dewarped box strip, built once per pass and shared by every downstream
/// stage (duel / PULC routing / recognition / matte).
struct DewarpedStrip {
    cv::Mat strip;
    /// The strip's band region in upright-image space — the dewarped (matte)
    /// strip maps affinely onto this rect (contour curl approximated).
    OrientedRect region;
};

/// Killed ink alpha from the glyph-matte run, in canonical-strip space
/// (48 rows x w cols), with the strip rotation recorded for un-rotation into
/// the dewarp band region during erasure.
struct MatteMask {
    std::vector<uint8_t> alpha;
    int w = 0;
    int rot = 0; // 90° CW turns applied before the matte run (0..3)
};

/// Erased-background patch for one line: the source's own pixels with the ink
/// replaced by the block-median field, alpha feathered at the edges. Immutable
/// after build — the placement quad lives on the owning TextLine
/// (eraseCorners) so anchor copies re-pose without cloning pixels.
struct ErasedStrip {
    std::vector<uint8_t> rgba; // w*h*4, R,G,B,A interleaved (Bitmap ARGB_8888 layout)
    int w = 0;
    int h = 0;
    int epoch = -1; // Engine::runErase epoch the patch was built in
};

struct TextLine {
    TextBox box;
    std::u32string text;
    float score = 0;
    std::vector<CharFiring> firings;
    bool bold = false;
    int fgColor = 0xFF000000;
    int bgColor = 0xFFFFFFFF;
    int blockId = -1;          // paragraph block (same id = one translation unit)
    int stripIdx = -1;         // source box's strip in the pass's DewarpedStrip cache
    std::shared_ptr<MatteMask> matte;
    std::shared_ptr<ErasedStrip> erase;
    Point eraseCorners[4] = {}; // padded render region (valid iff erase); re-posed per frame
};

/// One MNN model with session management. Precision Low (fp16) + Memory Low
/// (dynamic int8 GEMM on sdot cores; High otherwise — the weight-dequant path
/// below High routes through the slow per-tile executor). The matte model
/// specifically regressed on Memory_High (slower AND under-covering), so no
/// per-model memory-mode override exists.
struct Model {
    std::unique_ptr<MNN::Interpreter> interpreter;

    struct Session {
        MNN::Session* session = nullptr;
        explicit Session(MNN::Interpreter* net, int threads);
        ~Session() = default; // owned by interpreter
    };

    static std::unique_ptr<Model> load(const std::string& path);

    std::unique_ptr<Session> session(int threads);

    /// Runs the model on input data with shape [N,3,H,W]; returns output data
    /// (moved out of the host copy) and shape. The input tensor is refetched
    /// from the session after resize (never cache MNN tensors).
    std::vector<float> run(MNN::Session* session,
                           const std::vector<float>& data,
                           const std::vector<int>& shape,
                           std::vector<int>& outShape);
};

/// Script recognizer: N parallel single-threaded sessions + CTC charset.
struct Recognizer {
    std::vector<std::unique_ptr<Model::Session>> sessions;
    std::unique_ptr<Model> model;
    std::vector<char32_t> charset;
    bool rtl = false;

    static std::unique_ptr<Recognizer> load(const std::string& modelPath,
                                            const std::string& vocabPath,
                                            bool rtl);
};

// ---- homography.cc ----
namespace hmat {
using H9 = std::array<float, 9>;
constexpr H9 IDENTITY{1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f};
H9 matMul(const H9& a, const H9& b);
/// Gauge-normalizes h so h[8] == 1 exactly; false on a degenerate gauge.
bool normalize(H9& h);
bool project(const H9& h, float x, float y, float& qx, float& qy);
bool invert(const H9& h, H9& out);
/// Correspondences as {px, py, qx, qy} with (px,py) -> (qx,qy).
bool fitHomography(const std::vector<std::array<float, 4>>& pairs, H9& out);
bool fitAffine(const std::vector<std::array<float, 4>>& pairs, H9& out);
bool fitSimilarity(const std::vector<std::array<float, 4>>& pairs, H9& out);
}

// ---- anchor.cc ----
namespace anchor {

constexpr int BRIEF_BYTES = 32;
using Descriptor = std::array<uint8_t, BRIEF_BYTES>;

struct FeatureSet {
    std::vector<cv::Point2f> kps;
    std::vector<Descriptor> descs;
};

/// FAST-9 (+subpixel, NMS radius 3) and oriented BRIEF-256, with the Locked-
/// path soft-threshold fallback (t=15, redetect at t=7 under 200 keypoints).
FeatureSet computeFeatures(const cv::Mat& gray);

struct DescMatch { int anchorIdx, frameIdx, distance; };
/// Brute-force nearest + Lowe ratio (best/second-best hamming).
std::vector<DescMatch> matchLowe(const std::vector<Descriptor>& anchor,
                                 const std::vector<Descriptor>& frame, float ratio);

/// Guided matcher (reference match_descriptors_guided): each anchor keypoint
/// projects through the hView prior; only frame keypoints inside
/// [radiusPx] of the prediction are candidates, so blur-degraded
/// descriptors survive the windowed Lowe ratio against a handful of
/// competitors instead of the global pool. Exactly-one-candidate windows
/// fall back to an absolute Hamming threshold (the prior vouches for the
/// position; no second-best exists to ratio against).
std::vector<DescMatch> matchGuided(const FeatureSet& anchor, const FeatureSet& frame,
                                   float ratio, const hmat::H9& prior, float radiusPx);

struct TrackFit {
    hmat::H9 h;
    int inliers = 0;
    float medianResidualPx = 1e30f;
    std::vector<std::array<float, 4>> inlierPairs; // (anchor x,y, view x,y)
};

/// PROSAC-phased RANSAC homography with prior-penalty scoring + adaptive
/// complexity refit (homography >= 30 inliers, affine >= 15, else similarity).
/// False below [minInliers].
bool ransacHomography(const std::vector<std::array<float, 4>>& pairs,
                      const hmat::H9* prior, int minInliers, TrackFit& out);

/// RANSAC similarity for the coarse KLT delta (reference ransac_similarity).
bool ransacSimilarity(const std::vector<std::array<float, 4>>& pairs,
                      int minInliers, hmat::H9& out);

/// 8-DoF EKF over the homography (h8 = 1 gauge), per-inlier sequential updates.
class HomographyEkf {
public:
    explicit HomographyEkf(const hmat::H9& h);
    void reset(const hmat::H9& h);
    void predict();
    void updatePairs(const std::vector<std::array<float, 4>>& pairs);
    const hmat::H9& homography() const { return _h; }
private:
    hmat::H9 _h;
    double _p[8][8];
};

} // namespace anchor

class Engine {
public:
    Engine();  // starts the relocalize worker thread
    ~Engine(); // stops + joins it

    bool setDetector(const std::string& path, int threads);
    bool setScriptClassifier(const std::string& path, std::vector<std::string> routes);
    bool setGlyphMatte(const std::string& path);
    bool setAligner(const std::string& path);
    bool addRecognizer(const std::string& key, const std::string& modelPath, const std::string& vocabPath);

    /// One RAW sensor frame (rotated upright inside). Profile::Live frames
    /// route through the anchor tracker (acquire / relocalize / stillness
    /// gate — a no-helper frame may return {}), Profile::Still runs the full
    /// pass. [forcedKey] empty = auto script routing via the classifier.
    std::vector<TextLine> analyze(const uint8_t* rgba, int width, int height,
                                  int rotationDegrees, Profile profile,
                                  const std::string& forcedKey);

    /// Anchorless frames still feed the stillness gate: the async acquire
    /// design has the GL thread probing each frame so QUIET_MEAN_ABS_DIFF
    /// diffs land frame-to-frame (the acquire worker alone is throttled too
    /// sparsely for the 200 ms quiet window to ever open). NEVER blocks the
    /// presenter: the engine mutex is try-locked, and the frame is skipped
    /// when the acquire worker holds it. False when skipped.
    bool probeStillness(const uint8_t* rgba, int width, int height, int rotationDegrees);

    /// One presenter tick against the live anchor (stillness + KLT coarse
    /// pose + reloc dispatch), packing the CURRENT pose and content cursor.
    /// False when no anchor is held after the tick (caller re-acquires).
    bool liveTick(const uint8_t* rgba, int width, int height, int rotationDegrees,
                  hmat::H9& h, uint64_t& epoch, uint64_t& contentVersion);

    /// Live-tick presentation cache accessors — content pulls marshal the
    /// LAST emission with strip bytes gated on its captured epoch.
    const std::vector<TextLine>& lastLiveLines() const { return _lastLiveLines; }
    int lastLiveEpoch() const { return _lastLiveEpoch; }

    /// Current erasure epoch; JNI marshals strip pixels only for lines built
    /// in this epoch (fresh acquire passes, incl. stills).
    int currentEraseEpoch() const { return _eraseEpoch; }

    /// Whether the analyze()/liveTick() call in progress rebuilt erasure strips.
    /// JNI gates pixel marshaling on this (NOT on the epoch alone): projected
    /// lines on locked ticks still carry the acquire's epoch, so epoch-equality
    /// re-sent megabytes and store-reset the Kotlin side every analyze tick —
    /// evicting entries for lines momentarily culled at the frame edge, which
    /// showed as text without its erased background on re-entry.
    bool builtStripsThisCall() const { return _builtStripsThisCall; }

    std::string dominantRecognizerKey() const { return _dominantKey; }

private:
    friend struct DetectorTask;

    std::unique_ptr<Model> _detector;
    float _detStride = 1.0f; // model-input px per probability-map px, probed at load
    float _detScale = 1.0f;  // upright-image px per detector-content px of the last detect()

    /// Det stride pool compensation (the "mask-band deficit"), in
    /// upright-image px — same quantity wherever bands/strips are derived.
    float detPoolCompensationPx() const {
        return std::max(0.0f, _detStride - 1.0f) * _detScale;
    }
    std::unique_ptr<Model::Session> _detSession;

    std::unique_ptr<Model> _pul;
    std::vector<std::unique_ptr<Model::Session>> _pulSessions;
    std::vector<std::string> _routes; // 10: PULC class -> recognizer key ("" = none)

    std::unique_ptr<Model> _matte;
    std::vector<std::unique_ptr<Model::Session>> _matteSessions;

    std::unique_ptr<Model> _aligner;
    std::unique_ptr<Model::Session> _alignerSession;

    // ---- live anchor tracking state (track.cc / anchor.cc) ----
    // Single active anchor: canonical-frame features (FAST+BRIEF), the full
    // OCR pass's lines as canonical overlays, the anchor→view homography pose,
    // the coarse tracker ring (KLT seeds + prev frame), and the pose ring the
    // relocalize worker's corrections weave into (reference coarse_tracker.rs).
    struct AnchorState {
        cv::Mat gray;                        // canonical frame (upright gray)
        anchor::FeatureSet features;         // canonical FAST+BRIEF
        int rotation = -1;
        std::vector<TextLine> lines;         // canonical overlays
        hmat::H9 hView = hmat::IDENTITY; // latest anchor→view pose
        std::vector<std::array<float, 4>> seeds;      // (anchor x,y, view x,y) KLT seeds
        cv::Mat prevGray;                    // the view frame the seeds' view side lives in
        cv::Mat prevGraySmall;               // half-scale twin for the coarse KLT —
                                             // full-res pyramid builds were the 40 ms/tick
                                             // hotspot; all pose math stays full-res
        int relocFailures = 0;
        /// Consecutive coarse-defense gate misses presenting the last pose
        /// (hold-with-budget — see COARSE_MAX_FAILURES in track.cc).
        int coarseFailures = 0;
        /// (monotonic frame idx, pose after that tick) — weave base for late corrections.
        std::deque<std::pair<uint64_t, hmat::H9>> poseRing;
    };
    std::unique_ptr<AnchorState> _anchor;
    uint64_t _frameSeq = 0;                    // presenter-side tick counter
    uint64_t _anchorEpoch = 0;                 // bumped on storeAnchor/dropAnchor

    /// Dropped anchors (small LRU; reference try_cached_anchors): features +
    /// canonical overlays survive a drop so a scene returning into view
    /// re-locks in one tick instead of waiting out the stillness gate +
    /// full pipeline. Erase strips ride along via shared_ptr (zero recompute).
    struct CachedAnchor {
        int rotation = -1;
        int grayCols = 0;
        int grayRows = 0;
        anchor::FeatureSet features;
        std::vector<TextLine> lines;
    };
    std::deque<std::unique_ptr<CachedAnchor>> _anchorCache;
    /// Best-effort re-lock against the anchor cache; on success _anchor is
    /// restored with the refit pose and true is returned.
    bool relockCached(const cv::Mat& gray, int rotationDegrees);

    // Relocalize worker (reference TrackerCompute): single slot, drop-if-busy.
    // The worker owns its correction-side filter state (EKF, inlier EMA, freeze
    // budget, last accepted H); the presenter only weaves accepted corrections.
    struct RelocRequest {
        uint64_t epoch = 0;
        uint64_t frameIdx = 0;
        cv::Mat gray;
        hmat::H9 prior = hmat::IDENTITY;
        anchor::FeatureSet features;
        int anchorW = 0;
        int anchorH = 0;
    };
    enum class RelocKind { Accepted, Frozen, Rejected };
    struct RelocResult {
        uint64_t epoch = 0;
        uint64_t frameIdx = 0;
        RelocKind kind = RelocKind::Rejected;
        hmat::H9 h = hmat::IDENTITY;
        std::vector<std::array<float, 4>> inlierPairs;
    };
    void relocLoop();
    RelocResult relocWorkerTick(const RelocRequest& req);
    static std::optional<anchor::TrackFit> relocFitFor(const RelocRequest& req,
                                                       const hmat::H9& lastH);
    void dispatchReloc(const cv::Mat& gray, uint64_t frameIdx);
    void applyRelocResult();

    std::thread _relocThread;
    std::mutex _relocMutex;                    // request/result/busy/stop + dispatch pacing
    std::condition_variable _relocCv;
    std::optional<RelocRequest> _relocRequest;
    std::optional<RelocResult> _relocResult;
    bool _relocInflight = false;
    bool _relocStop = false;
    // Presenter-side dispatch pacing: the worker would otherwise chain
    // back-to-back jobs (drop-if-busy collapses the gap), holding ~50-100%
    // of a core through a pan for no visible gain.
    std::chrono::steady_clock::time_point _relocLastDispatch{};

    // Worker-local (touched from _relocThread only) — keyed to the request epoch.
    uint64_t _relocWorkerEpoch = 0;
    std::unique_ptr<anchor::HomographyEkf> _relocEkf;
    float _relocInlierEma = 0.0f;
    int _relocFreezeBudget = 3;
    hmat::H9 _relocLastH = hmat::IDENTITY;

    /// Locked anchor tick (track + anchored analyze): poll/apply the worker's
    /// correction, coarse KLT, ring push, dispatch the next relocalize job.
    /// [small] is the caller-built half-scale of [gray], shared per tick with
    /// the stillness gate instead of each stage downsampling the full frame.
    std::vector<TextLine> lockedTick(const cv::Mat& gray, const cv::Mat& small);

    /// Full pass over one already-upright RGB/RGBA frame (3 or 4 channels).
    std::vector<TextLine> runFullPipeline(const cv::Mat& upright, Profile profile,
                                          const std::string& forcedKey);
    /// Live entry for analyze(): anchor acquire / relocalize / quiet gate.
    std::vector<TextLine> analyzeLive(const cv::Mat& upright, const std::string& forcedKey,
                                      int rotationDegrees);

    /// Canonical overlays projected through the current homography.
    std::vector<TextLine> projectOverlays();
    /// Build/replace the anchor from an upright canonical frame + its lines.
    /// On too few features the anchor stays down (the acquire shows nothing).
    void storeAnchor(const cv::Mat& gray, std::vector<TextLine> lines, int rotationDegrees);
    /// Lost: clears anchor + coarse state.
    void dropAnchor();
    /// Anchor validity for the current frame: rotation/size must match.
    bool anchorMismatch(int rotationDegrees, const cv::Size& size) const {
        return _anchor && (_anchor->rotation != rotationDegrees ||
                           _anchor->gray.size() != size);
    }
    /// KLT coarse step against the anchor; gate misses hold the pose with a
    /// budget (coarseHold), sustained misses drop the anchor. [small] is the
    /// half-scale twin of [gray] built once per tick by the caller.
    std::vector<TextLine> coarseTrack(const cv::Mat& gray, const cv::Mat& small);
    /// Coarse-defense miss with budget: presents the last pose with state
    /// untouched so an in-flight reloc can resurrect the defense; drops the
    /// anchor only past COARSE_MAX_FAILURES.
    std::vector<TextLine> coarseHold();
    /// Frame-to-frame stillness gate (128-px thumbnail mean-abs-diff, rolling;
    /// [small] is the tick's half-scale twin of the gray frame, not full-res).
    void updateStillness(const cv::Mat& small);
    bool quietEnough() const;

    // Stillness state (reference: IMU 200 ms quiet window before acquire).
    cv::Mat _stillGray;
    std::chrono::steady_clock::time_point _stillSince{};

    struct RecognizerSlot {
        std::string key;
        std::string modelPath;
        std::string vocabPath;
        bool rtl = false;
        std::once_flag once;
        std::unique_ptr<Recognizer> recognizer;
    };
    std::vector<std::unique_ptr<RecognizerSlot>> _slots;
    std::string _dominantKey;

    /// Duel decode of one box at the winning rotation; recognize() reuses it
    /// when routing keeps the box on the duel's recognizer and no whitespace
    /// split would re-cut the strip (rot dims recorded post-rotation).
    struct DuelCacheEntry {
        Decoded decoded;
        bool valid = false;
        int rotCols = 0;
        int rotRows = 0;
    };
    std::vector<DuelCacheEntry> _duelCache;
    std::string _duelKey;

    /// Serializes model loading against analyze(): the CameraX analyzer pool
    /// can call analyze() while load() still swaps modules.
    std::mutex _mutex;

    Recognizer* recognizer(const std::string& key);

    /// Detection: heatmap -> filtered boxes with contours, upright image space.
    std::vector<TextBox> detect(const cv::Mat& upright, Profile profile);

    /// Dewarp every box once (parallel); all downstream stages consume the
    /// shared strips instead of re-warping the same geometry per stage.
    std::vector<DewarpedStrip> buildStrips(const cv::Mat& image,
                                           const std::vector<TextBox>& boxes);

    /// Scene reading quadrant via recognizer-confidence duel (R0 fallback).
    /// Winning-rotation decodes are cached in _duelCache for recognize().
    int canonicalQuadrant(std::vector<TextBox>& boxes,
                          const std::vector<DewarpedStrip>& strips,
                          Recognizer* duel);

    /// Per-box per-class PULC predictions -> recognizer keys (dominant fallback,
    /// minority fold, latin-into-non-latin merge). Parallel over _pulSessions.
    std::vector<std::string> routeScripts(const cv::Mat& upright, const std::vector<TextBox>& boxes,
                                          const std::vector<DewarpedStrip>& strips, int canonical);

    /// Recognize all boxes (duel cache hits skip the model run entirely) with
    /// whitespace splitting. Rejected lines drop out (empty text).
    std::vector<TextLine> recognize(std::vector<TextBox>& boxes,
                                    const std::vector<DewarpedStrip>& strips,
                                    const std::vector<std::string>& keys, int canonical,
                                    Profile profile);

    /// Glyph-matte typography (bold flag + fg/bg colors) per line; no-op when
    /// no matte model was set. Successful runs also store the killed ink alpha
    /// on the line for the erasure stage.
    void applyGlyphMatte(const cv::Mat& upright, std::vector<TextLine>& lines,
                         const std::vector<DewarpedStrip>& strips, int canonical);

    /// Text erasure (erase.cc): each line gets an ErasedStrip — the line's
    /// padded render region with the ink replaced by the block-median field
    /// (reference background_field). Mask = union of all lines' projected
    /// matte alpha (rim-dilated per strip); matte-less lines fall back to a
    /// far-from-paper distance mask.
    void runErase(const cv::Mat& upright, std::vector<TextLine>& lines,
                  const std::vector<DewarpedStrip>& strips);

    /// Bumped per runErase call; JNI marshals strip pixels only for the fresh epoch.
    int _eraseEpoch = 0;

    /// Set false at analyze() entry, true by runErase — see builtStripsThisCall().
    bool _builtStripsThisCall = false;

    // ---- live tick presentation cache (GL sink slimming) ----
    // Live lines cross JNI only when content moves: tracked frames re-pose
    // the same canonical overlays, so per-frame JNI marshaling was pure
    // overhead (the Pixel measured 28–55 ms sinks). projectOverlays() is the
    // sole live emission point and owns this cache.
    std::vector<TextLine> _lastLiveLines;
    /// Strip epoch belonging to _lastLiveLines (-1 = no strips to marshal).
    int _lastLiveEpoch = -1;
    /// Bumped when live CONTENT (not pose) changed: fresh acquire, refresh,
    /// cached re-lock. The tick packs it; Kotlin pulls liveContent() on moves.
    uint64_t _liveContentVersion = 0;

    /// Paragraph block grouping: union-find over quadrant-frame mergeability
    /// (blocks.cc); assigns blockId in reading order and re-sorts the
    /// lines vector so each block's lines are consecutive.
    void assignBlocks(std::vector<TextLine>& lines);

    /// Canonical per-block rect snap (blocks.cc): shared angle + column
    /// centerline per block (own widths/heights kept), run once per acquire
    /// before erase + corner rewrite so all consumers render the same snapped
    /// geometry.
    void snapBlockTightRects(std::vector<TextLine>& lines);

    /// docaligner corner-heatmap document rectification (stills only).
    /// Returns inverse-warp matrix mapping warped coords back to the original.
    bool alignDocument(const cv::Mat& upright, cv::Mat& warped, cv::Mat& inverse);

    void orientBoxes(std::vector<TextBox>& boxes);
};

// ---- dewarp.cc ----
void fitTiltFieldConsensus(std::vector<TextBox>& boxes);

/// Dewarps a contour's text band to a natural-scale strip (p05–p95 spine
/// spread x STRIP_BAND_INFLATE + descender slice, u padded by one band
/// thickness each side, ppocr.rs contour_strip_warp). [thicknessPad] restores
/// the mask-band deficit: (detStride-1) in image px. False for degenerate
/// contours (caller falls back to the oriented-rect affine strip).
/// [regionOut] receives the band region in image space the strip maps
/// affinely onto (contour curl approximated linearly).
bool dewarpContour(const cv::Mat& rgb, const TextBox& box, float thicknessPad, cv::Mat& out,
                   OrientedRect* regionOut = nullptr);

/// Oriented-rect corners in reading order (TL->BR), hw/hh grown by
/// [padX]/[padY]. Shared by dewarp, erase-strip placement and the overlay
/// render-quad rewrite — they must agree bit-for-bit.
void rectCorners(float cx, float cy, float angle, float w, float h,
                 float padX, float padY, Point out[4]);

/// DB unclip distance, aspect-independent (long-box limit ratio*t/2, floored
/// at 1 px) — [t] is the band thickness in image px. Shared by detection
/// unclip, strip-band inflate and the render envelope: one formula, three
/// call sites.
inline float detUnclipDistance(float t) {
    return std::max(DET_UNCLIP_RATIO * t / 2.0f, 1.0f);
}

/// Overlay render quad expansion off the tight band: unclip distance + pool
/// compensation + box border (erase envelope math must equal this).
inline float renderExpandDistance(float bandH, float poolComp) {
    return detUnclipDistance(bandH) + poolComp + DET_BOX_BORDER;
}

/// Tight-anchored band envelope shared by the matte strip shape and the erase
/// union projection (kernel x STRIP_BAND_INFLATE + descender slice, centre
/// pushed down half the slice, one kernel pad width each side). Both stages
/// must agree bit-for-bit — this helper makes that structural.
inline OrientedRect stripBandRegion(const OrientedRect& tight) {
    const float kernel = std::max(tight.height, 1.0f);
    const float descExtra = kernel * STRIP_DESCENDER_VPAD_FRAC;
    OrientedRect band = tight;
    band.height = kernel * STRIP_BAND_INFLATE + descExtra;
    band.cx += -std::sin(band.angle) * descExtra * 0.5f;
    band.cy += std::cos(band.angle) * descExtra * 0.5f;
    band.width += 2.0f * kernel;
    return band;
}

/// Strip-level helper for the matte/color stage: same geometry as
/// dewarpContour, falling back to the inflated band rect on degenerate
/// contours. Always succeeds (replicate-edge affine at worst).
void warpRect(const cv::Mat& rgb, const OrientedRect& o, int outW, int outH, cv::Mat& out);
void dewarpLine(const cv::Mat& rgb, const TextBox& box, float thicknessPad, cv::Mat& out,
                OrientedRect* regionOut = nullptr);

/// In-place 90°-step rotation (0/90/180/270 CW); other degrees leave the frame.
void rotateByDegrees(cv::Mat& image, int degrees);

/// Rotates a strip [times]x 90° clockwise (0..3).
cv::Mat rot90(const cv::Mat& image, int times);

/// Reading rotation for a box's strip under scene [canonical] (0..3 CW).
int stripRotation(const TextBox& box, int canonical);

/// PCA principal-axis angle (ux >= 0 canonical), empty for degenerate sets.
bool principalAxisAngle(const std::vector<Point>& contour, float& angle);

// ---- recognize.cc ----
std::vector<char32_t> loadCharset(const std::string& path);
std::u32string repairCyrillicWordMixing(const std::u32string& text);

} // namespace ocr
