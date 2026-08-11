//
// Created by Ricardo Snoek on 01/08/2026.
//

#include "whisper.h"

#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

// JNI bindings for whisper.cpp. Package root: app.versta.translate.bridge.whisper
//
// Utterance-batch design: audio is buffered continuously via feed() and
// segmented into discrete utterances using whisper.cpp's own VAD segmenter
// (whisper_vad_segments_from_samples). Each utterance is transcribed with
// exactly one whisper_full call over its own audio span — no sliding window,
// no seam commit, no DTW token timestamps.
//
// Context continuity across utterances comes from Whisper's own prompt-
// conditioning mechanism (params.prompt_tokens), not a streaming seam: each
// utterance's decoded text primes the next one within a session automatically
// (see Recognizer::carried_prompt_ids), and setCarriedContext() lets Kotlin
// re-seed that chain across sessions (e.g. resuming a conversation after a
// language swap) or leave it empty so the static per-language initial_prompt
// (SpeechRecognitionInitialPrompts, in
// core/entity/SpeechRecognitionInitialPrompts.kt) is used instead.

// Opaque handle packing the whisper model + optional Silero VAD context.
struct WhisperModelHandle {
    whisper_context *ctx;
    whisper_vad_context *vctx;
    int n_threads;

    WhisperModelHandle(whisper_context *c, whisper_vad_context *v, int t)
        : ctx(c), vctx(v), n_threads(t) {}
};

// Guards whisperModelInstances: create runs on the load thread while
// destroy runs on the teardown thread, and lookup-for-create runs on the
// recognizer load thread — the map cannot be assumed single-threaded.
std::mutex whisperModelInstancesMutex;
std::unordered_map<jlong, std::unique_ptr<WhisperModelHandle>> whisperModelInstances;
jlong whisperModelInstanceCounter = 0;

static WhisperModelHandle *findModel(jlong handle) {
    std::lock_guard<std::mutex> lock(whisperModelInstancesMutex);
    auto it = whisperModelInstances.find(handle);
    return it != whisperModelInstances.end() ? it->second.get() : nullptr;
}


extern "C" {

static std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) {
        return "";
    }
    const char *cstr = env->GetStringUTFChars(s, nullptr);
    std::string out(cstr != nullptr ? cstr : "");
    if (cstr != nullptr) {
        env->ReleaseStringUTFChars(s, cstr);
    }
    return out;
}


// ---------------------------------------------------------------------------
// WhisperModel
// ---------------------------------------------------------------------------


JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_whisper_WhisperModel_create(
        JNIEnv *env,
        jobject,
        jstring modelPath,
        jstring vadModelPath,
        jint nThreads
) {
    const std::string model = jstr(env, modelPath);
    const std::string vad_model = jstr(env, vadModelPath);


    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // no GPU backend on Android
    // flash_attn is intentionally disabled: it causes a 4x performance
    // regression on Tiny/Small models via the non-CTranslate2 attention path.
    // Dropping DTW below removes flash_attn's OTHER constraint (whisper.cpp
    // refuses dtw_token_timestamps + flash_attn together), but re-testing
    // flash_attn on its own merits is a deliberately separate change, not
    // bundled into this one.
    cparams.flash_attn = false;
    // DTW per-token timestamps existed only to power the old streaming seam
    // commit (see the file header comment) — the utterance-batch design has
    // no seam, so this is off. This also removes a full extra decode of the
    // whole token sequence per pass (previously visible as prompt_ms/n_prompt
    // in the metrics).
    cparams.dtw_token_timestamps = false;


    whisper_context *ctx =
              whisper_init_from_file_with_params(model.c_str(), cparams);
    if (ctx == nullptr) {
        return 0;
    }


    whisper_vad_context *vctx = nullptr;
    if (!vad_model.empty()) {
        whisper_vad_context_params vparams = whisper_vad_default_context_params();
        // Silero's graph is tiny; thread-sync overhead exceeds the compute, so
        // cap at 2 threads regardless of the model's n_threads.
        vparams.n_threads = std::min(2, static_cast<int>(nThreads));
        vparams.use_gpu = false;
        vctx = whisper_vad_init_from_file_with_params(vad_model.c_str(), vparams);
        if (vctx == nullptr) {
            whisper_free(ctx);
            return 0;
        }
    }


    // Warmup: run a throwaway whisper_full on 0.5 s of silence to page in
    // the mmap'd weights and finish any KleidiAI repack before the first
    // real pass. Without this the first utterance pays the multi-second
    // cold-start page-fault cost (visible as 8+ s aborts on large models).
    {
        const int n_warm = WHISPER_SAMPLE_RATE / 2;
        std::vector<float> zeros(n_warm, 0.0f);
        whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        wp.n_threads = nThreads;
        wp.single_segment = true;
        wp.no_timestamps = true;
        wp.no_context = true;
        wp.language = "en";
        wp.suppress_nst = true;
        wp.temperature_inc = 0.0f;
        wp.max_tokens = 8;
        wp.audio_ctx = 256;
        whisper_full(ctx, wp, zeros.data(), n_warm);
    }


    auto handle = std::make_unique<WhisperModelHandle>(ctx, vctx, nThreads);
    std::lock_guard<std::mutex> lock(whisperModelInstancesMutex);
    jlong id = ++whisperModelInstanceCounter;
    whisperModelInstances[id] = std::move(handle);
    return id;
}


JNIEXPORT void JNICALL Java_app_versta_translate_bridge_whisper_WhisperModel_destroy(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    std::unique_ptr<WhisperModelHandle> model;
    {
        std::lock_guard<std::mutex> lock(whisperModelInstancesMutex);
        auto it = whisperModelInstances.find(handle);
        if (it == whisperModelInstances.end()) {
            return;
        }
        model = std::move(it->second);
        whisperModelInstances.erase(it);
    }
    if (model->vctx != nullptr) {
        whisper_vad_free(model->vctx);
    }
    if (model->ctx != nullptr) {
        whisper_free(model->ctx);
    }
}


// ---------------------------------------------------------------------------
// WhisperRecognizer — utterance-batch transcription
// ---------------------------------------------------------------------------


// Utterance boundaries are derived ENTIRELY from a cheap per-poll VAD probe
// (probe_speech, below) over a fixed trailing window — never from a
// whole-buffer segmentation. An earlier design used whisper.cpp's own
// segmenter (whisper_vad_segments_from_samples) to get precise bounds once a
// boundary looked likely, gated by a raw-buffer condition (buffered_ms vs
// max_utterance_ms). That gate and the segmenter's own notion of "ready"
// measured different quantities (raw buffer vs. VAD-trimmed group) and could
// disagree — the segmenter is trimmed, so on continuous speech at the cap it
// could report neither "hit cap" nor "confirmed silence", triggering a
// fallback that dropped the entire buffer. Confirmed dropping a full 28s
// utterance this way. See git history for that version.
//
// The fix: a single probe supplies every signal (onset, last speech frame,
// last voiced frame, best pause to cut at), so "is it ready" and "where are
// the bounds" can never come from different measurements of the buffer.
static const int VAD_SPEECH_PAD_MS = 200;  // generous LEADING edge pad so an onset is never clipped
// Hysteresis below vad_threshold at which a frame is still plausibly speech.
// Mirrors whisper.cpp's own segmenter (whisper_vad_segments_from_probs),
// which opens a segment at `threshold` but only closes it below
// `threshold - 0.15` — a window END bound built on `threshold` alone stops at
// the last confidently-voiced frame and clips trailing fricatives.
static const float VAD_HYSTERESIS = 0.15f;
// Consecutive above-threshold frames required before an onset is latched.
// whisper_vad_detect_speech resets the VAD's LSTM state on every call, so the
// first frames of every scan are warm-up transients; a single-frame spike
// there would latch the onset up to a full scan window too early.
static const int SPEECH_ONSET_FRAMES = 3;  // ~96ms at the Silero window size (512 samples)
// Shortest run of sub-hysteresis frames worth treating as a pause when the
// max-utterance cap forces a cut, rather than slicing mid-word.
static const int CAP_CUT_MIN_PAUSE_MS = 150;
// Padding added after the last voiced frame when an endpoint closes a
// window. Provably safe: an endpoint only fires once confirmed silence
// (endpoint_silence_ms) already covers this region, so the pad can never
// reach into a following utterance. Clamped per-recognizer at create() to
// at most half of endpoint_silence_ms, in case that is configured smaller
// than this default.
static const int UTTERANCE_END_PAD_MS = 300;
// Padded windows shorter than this hold no syllable worth a decode pass.
// Deliberately permissive — this is measured on 0.8-threshold frames, so a
// stricter filter risks silently deleting a genuine short word ("yes") whose
// confident core can be under 200ms. A click that clears this bar still
// costs one wasted decode, but is then caught by the no_speech gate below;
// nothing is ever dropped while speech is outstanding.
static const int MIN_TRANSCRIBE_WINDOW_MS = 400;
// flush() has no endpoint confirmation to lean on, so unlike
// MIN_TRANSCRIBE_WINDOW_MS (a window LENGTH floor) it additionally requires
// this much actual watermark-confirmed speech CONTENT before decoding —
// otherwise the ~2.5s of trailing silence process() always leaves buffered
// (see ENDPOINT_SCAN_WINDOW_MS) gets handed to whisper_full on every stop(),
// which is exactly what caused silence hallucinations. Stays under a clipped
// one-word "yes".
static const int MIN_SPEECH_MS = 200;


// Absolute headroom (ms) feed() allows `pending` to grow beyond one
// max-length utterance before it starts dropping the oldest audio. Purely a
// safety valve against unbounded growth if process() goes unpolled for a
// while (e.g. app backgrounded) — normal operation never approaches it.
static const int MAX_PENDING_SLACK_MS = 5000;


// Commit-pass wall-clock budget, derived from the measured decode rate
// (ms of compute per second of window audio). BOTH the budget and its ceiling
// scale with window length; neither did in the old streaming design, which
// was survivable when every window was a fixed ~2.6 s but is fatal here.
// A 26 s utterance legitimately needs several seconds of compute, so a flat
// 4 s floor aborted every long utterance — and since an aborted pass produced
// no EMA sample, the budget never grew and every long utterance aborted
// forever, silently dropping its audio.
static const double COMMIT_BUDGET_FACTOR = 3.0;
static const long COMMIT_BUDGET_FLOOR_MS = 4000;
static const long COMMIT_BUDGET_CEILING_MS = 12000;
// Cold-start decode rate (ms of compute per second of window audio) assumed
// before the EMA has a sample: the device may need as long as the audio
// itself. Anything slower than real time cannot sustain dictation at all, so
// a tighter cold budget buys nothing except aborted real utterances.
static const double COMMIT_BUDGET_COLD_RATE_MS_PER_SEC = 1000.0;
// The ceiling never falls below this multiple of the window duration, so a
// long utterance cannot be clamped down to a budget shorter than its own
// audio.
static const double COMMIT_BUDGET_MAX_RATIO = 2.0;
// Smoothing factor for the measured commit decode rate.
static const double COMMIT_EMA_ALPHA = 0.3;


// How much trailing audio each poll's probe_speech() scan covers, in the
// common case. It only has to answer "how long has it been quiet" and
// "where's the nearest pause", not resolve every speech segment in the
// buffer, so a fixed trailing window keeps the probe's cost constant instead
// of growing with utterance length. Must comfortably exceed
// endpoint_silence_ms. process() actually scans back to
// Recognizer::probed_until_ms when that is further back than this window
// (i.e. after a poll stall), so this is a floor on scan cost, not a fixed
// one — an earlier design ran a whole-buffer VAD segmentation once a
// boundary looked likely, measured at ~650 ms per poll on a 28 s buffer
// (O(n^2) across an utterance, visible as the poll rate collapsing from the
// nominal 5/s to ~1.5/s); that segmenter is gone entirely now.
static const int ENDPOINT_SCAN_WINDOW_MS = 2500;


// Below this average per-token log-probability, an utterance's own decode is
// not trusted enough to prime the NEXT one as context. Matches whisper.cpp's
// own logprob_thold default (whisper_full_default_params), which uses the
// same threshold to judge whether ITS OWN decode should be treated as failed.
static const float CARRY_LOGPROB_THRESHOLD = -1.0f;


struct Recognizer {
    whisper_context *ctx;
    int n_threads;
    whisper_vad_context *vctx;  // shared from WhisperModelHandle, may be nullptr


    // FIFO of incoming float PCM (16 kHz mono) not yet consumed.
    std::mutex mtx;
    std::deque<float> pending;


    // Stream timeline (ms). consumed_samples = total samples drained so far,
    // so the stream position of pending.front() is consumed_samples/16000 ms.
    // Used only to give the Java callback absolute (not window-relative)
    // start/end timestamps — nothing here depends on any previous
    // utterance's timing, unlike the old seam design.
    int64_t consumed_samples = 0;


    // Stream time (ms, absolute) of the end of the last frame the probe
    // scored as speech (strict vad_threshold — drives the endpoint decision).
    // Monotonic watermark: the probe only looks at a fixed trailing window
    // (ENDPOINT_SCAN_WINDOW_MS), so a poll that finds no speech means "quiet
    // recently", never "no speech ever" — the previous value must be
    // preserved, not cleared. `speech_until_ms > (stream position of
    // pending.front())` is what makes "there is un-transcribed speech
    // buffered" (`outstanding`) cheap to answer without re-segmenting the
    // whole buffer every poll.
    int64_t speech_until_ms = 0;

    // Stream time (ms, absolute) of the onset of the utterance currently
    // being accumulated, or -1 when none is outstanding. LATCHED, not
    // tracked: the probe scans a bounded trailing window and physically
    // cannot see the onset of a long utterance, so recomputing this every
    // poll would walk the start forward until only the tail survived.
    //
    // Invariant, maintained ONLY by advance_front(): (speech_start_ms >= 0)
    // == (speech_until_ms > stream position of pending.front()). Nothing
    // else may write either field.
    int64_t speech_start_ms = -1;

    // Stream time (ms, absolute) of the buffer end as of the last successful
    // probe: everything past this has never been scored by the VAD. Without
    // this, "the probe found no speech" only means "not in the trailing
    // ENDPOINT_SCAN_WINDOW_MS" — unsound whenever a poll stalls (a multi-
    // second decode can hold the pipeline while feed() keeps appending),
    // because audio the probe never actually saw would read as silence and
    // become droppable. Each poll extends its scan back to cover every
    // sample past this watermark, so "!outstanding" genuinely means "no
    // un-transcribed speech is buffered".
    int64_t probed_until_ms = 0;


    // VAD gate. If disabled (or no VAD model was bundled), there is no way to
    // auto-detect utterance boundaries: process() always reports "not ready"
    // and only flush() (on stop()) ever emits anything, transcribing
    // everything buffered as one shot per max_utterance_ms chunk.
    bool vad_enabled = true;
    float vad_threshold = 0.8f;


    // Confirmed trailing silence (ms) after the last detected speech before a
    // candidate utterance is considered finished and ready to transcribe —
    // this is what decides whether dictation has actually ended, as opposed
    // to a mid-sentence breath.
    int endpoint_silence_ms = 600;

    // min(UTTERANCE_END_PAD_MS, endpoint_silence_ms / 2), computed once at
    // create() — see UTTERANCE_END_PAD_MS for why the clamp is needed.
    int utterance_end_pad_ms = UTTERANCE_END_PAD_MS;


    // Forced-split ceiling (ms). Whisper's own decoder context is a hard 30s;
    // every transcribed window is unconditionally clamped to this length
    // (see process()), so a stretch of speech with no detected pause at all
    // (e.g. a sustained tone) still cannot produce a window whisper cannot
    // decode in one call.
    int max_utterance_ms = 15000;


    // Language hint passed to whisper_full (ISO 639-1 code or empty = auto).
    std::string language;
    // True when the user asked for auto-detection. After the first successful
    // transcription the detected language is pinned into `language` and this
    // is cleared, so subsequent utterances skip the extra decoder pass and
    // avoid language flapping between short utterances.
    bool language_auto = false;
    // The language code requested at create ("" for auto). Used by reset to
    // restore the auto-detection state between dictation sessions so a pinned
    // language from one session does not bleed into the next.
    std::string language_initial;


    // Per-language static priming text (see SpeechRecognitionInitialPrompts,
    // in core/entity/SpeechRecognitionInitialPrompts.kt), used as the
    // FALLBACK prompt when there is no carried context (session start with
    // nothing to carry from). Fixed for the Recognizer's lifetime;
    // reset() does not touch it — see language_initial for the same reasoning.
    std::string initial_prompt;
    // Token count of initial_prompt, for last_pass_n_batchd only (whisper_full
    // re-tokenizes the string itself on every pass that actually uses it).
    int initial_prompt_n_tokens = 0;


    // Active decoder context: the PREVIOUS utterance's own decoded token ids
    // (text tokens only, from a decode that passed the quality gate), used as
    // params.prompt_tokens for the NEXT utterance instead of initial_prompt.
    // whisper_full itself prefers prompt_tokens over initial_prompt whenever
    // both are set, so transcribe_utterance never needs to choose explicitly.
    //
    // Auto-chained internally after every utterance that passes the quality
    // gate (see transcribe_utterance) — this is intra-session only. Cleared
    // by reset(): cross-session persistence (surviving stop()/start(), a
    // language swap and back) is Kotlin's responsibility (SpeechContextStore),
    // which re-seeds this via setCarriedContext() before each session so
    // native never has to guess at a TTL it has no way to evaluate.
    //
    // Written only from transcribe_utterance and setCarriedContext, which the
    // Kotlin processMutex serialises against process()/flush() — the same
    // discipline `language` already relies on.
    std::vector<whisper_token> carried_prompt_ids;


    // Cap on how many tokens of decoded text are kept as carried context,
    // both this recognizer's own auto-chained output and whatever Kotlin
    // seeds via setCarriedContext. Mirrors whisper.cpp's own prompt-history
    // budget (n_text_ctx/2, see whisper_full_with_state's max_prompt_ctx) so
    // what we report to Kotlin is never longer than whisper would actually
    // use.
    int max_context_tokens = 224;


    // Hard wall-clock deadline for the ongoing whisper_full call. When the
    // abort_callback fires (whisper_abort_cb below), whisper_full bails with
    // a non-zero return code. Prevents temperature-fallback loops on
    // un-decodable audio from locking the pipeline.
    std::chrono::steady_clock::time_point abort_deadline;


    // Measured commit decode rate — ms of compute per second of window audio —
    // feeding the adaptive abort budget. Normalised by window duration because
    // utterances range from a couple of seconds up to max_utterance_ms.
    double commit_rate_ema_ms_per_sec = 0.0;


    // Post-decode gate: discard an utterance whose no_speech_prob exceeds
    // this. whisper's own suppression requires BOTH a high no_speech_prob AND
    // a low avg_logprob, so a *confident* hallucination on silence/music is
    // emitted regardless. Matches whisper's own no_speech_thold default
    // (wparams.no_speech_thold below) rather than sitting above it, since
    // this drops text outright with no fallback — the VAD gates upstream
    // (flush()'s speech-presence check, probe_speech()'s onset-run
    // requirement) are the primary defense against hallucination now.
    float no_speech_threshold = 0.6f;


    // Metrics: last-pass snapshot + per-session counters, read under mtx.
    // last-pass snapshot and processed_audio_sec/commit_compute_ms are
    // written inside transcribe_utterance right after whisper_full returns
    // (both count only actually-decoded audio); pass/abort/vad_skip counts
    // are updated there and in process().
    bool have_last_pass = false;
    long last_pass_elapsed_ms = 0;
    long last_pass_window_ms = 0;
    size_t last_pass_n_samples = 0;
    int last_pass_audio_ctx = 0;
    int last_pass_max_tokens = 0;
    long last_pass_budget_ms = 0;
    int last_pass_ret = 0;
    bool last_pass_was_flush = false;
    float last_pass_encode_ms = 0.0f;
    float last_pass_decode_ms = 0.0f;
    float last_pass_batchd_ms = 0.0f;
    // Run/token counts for the last pass, derived from whisper's timings
    // breakdown. With our params (single_segment, no_timestamps, one seek
    // iteration, greedy decode) whisper's own counters decompose as:
    //   n_encode = 1 (+1 on the first pass, when language auto-detect runs an
    //               extra encode inside whisper_full)
    //   n_decode = total tokens across segments (each greedy token is one
    //               single-token decode run)
    //   n_batchd = tokens in the initial prompt decode: the task prefix
    //               (sot + lang + transcribe + not, or sot + not for English)
    //               plus, when a prompt/carry is active, <|startofprev|> +
    //               its token length.
    // Totals = avg_ms * count, so these turn whisper's per-run averages into
    // per-pass totals.
    int last_pass_n_encode = 0;
    int last_pass_n_decode = 0;
    int last_pass_n_batchd = 0;
    long pass_count = 0;
    long abort_count = 0;
    long vad_skip_count = 0;
    double processed_audio_sec = 0.0;
    double commit_compute_ms = 0.0;


    // Java callback method ids cached once. callback_obj is a global ref;
    // the JNIEnv* is never stored — each JNI entry uses its own env parameter
    // (JNIEnv is thread-local per the JNI spec).
    jobject callback_obj = nullptr;
    jmethodID callback_method = nullptr;


    Recognizer(whisper_context *c, int t, whisper_vad_context *v)
            : ctx(c), n_threads(t), vctx(v) {}
};

// Guards whisperRecognizerInstances: create/destroy run on load/teardown
// threads while feed/process/getMetrics look up handles from the capture and
// process threads. Note the pointer returned by findRecognizer is used after
// the lock is released — safe because the Kotlin side serializes destroy
// behind the process/capture jobs (see WhisperSpeechRecognition
// .teardownSession); the lock itself protects the map from concurrent
// structural mutation (insert/rehash vs. lookup).
std::mutex whisperRecognizerInstancesMutex;
std::unordered_map<jlong, std::unique_ptr<Recognizer>> whisperRecognizerInstances;
jlong whisperRecognizerInstanceCounter = 0;

static Recognizer *findRecognizer(jlong handle) {
    std::lock_guard<std::mutex> lock(whisperRecognizerInstancesMutex);
    auto it = whisperRecognizerInstances.find(handle);
    return it != whisperRecognizerInstances.end() ? it->second.get() : nullptr;
}


// Moves the front of `pending` forward by `n` samples (already erased by the
// caller) and re-establishes the watermark invariants documented on
// Recognizer::speech_start_ms. This is the ONLY place consumed_samples may
// advance outside reset()/flush() — every consume site (feed()'s overflow
// drop, and every branch of process()) must route through it, or the
// invariant it maintains silently stops holding.
//
// Caller must hold rec->mtx.
static void advance_front(Recognizer *rec, size_t n) {
    if (n == 0) {
        return;
    }
    rec->consumed_samples += static_cast<int64_t>(n);
    const int64_t front_ms = rec->consumed_samples * 1000 / WHISPER_SAMPLE_RATE;

    if (rec->speech_until_ms <= front_ms) {
        // Everything the probe ever scored as speech is now behind the
        // front: either transcribed or deliberately dropped. Start clean.
        rec->speech_until_ms = 0;
        rec->speech_start_ms = -1;
    } else if (rec->speech_start_ms >= 0) {
        // Speech continues past the cut (the max-utterance cap path, or
        // feed()'s overflow drop truncating a live utterance). Re-anchor the
        // onset to the new front: clearing it here would make the very next
        // poll read !outstanding and TRIM AWAY the rest of a sentence that
        // is still in progress.
        rec->speech_start_ms = front_ms;
    } else {
        // speech_until_ms is ahead of the front with no onset latched: only
        // reachable if a probe call failed between the two updates. Latch
        // conservatively rather than leave the invariant violated.
        rec->speech_start_ms = front_ms;
    }
    // Nothing ahead of the new front has become unprobed by moving it
    // forward — only feed() (appending unprobed audio) can push this back.
    rec->probed_until_ms = std::max(rec->probed_until_ms, front_ms);
}


JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_whisper_Whisper_create(
        JNIEnv *env,
        jobject,
        jlong modelHandle,
        jboolean vadEnabled,
        jfloat vadThreshold,
        jstring language,
        jstring initialPrompt,
        jfloat noSpeechThreshold,
        jint endpointSilenceMs,
        jint maxUtteranceMs
) {
    auto model = findModel(modelHandle);
    if (model == nullptr) {
        return 0;
    }
    if (vadEnabled && model->vctx == nullptr) {
        return 0;
    }

    auto recognizer = std::make_unique<Recognizer>(model->ctx, model->n_threads, model->vctx);
    recognizer->vad_enabled = vadEnabled != JNI_FALSE;
    recognizer->vad_threshold = vadThreshold;
    recognizer->language = jstr(env, language);
    recognizer->language_initial = recognizer->language;
    recognizer->language_auto = recognizer->language.empty();
    recognizer->initial_prompt = jstr(env, initialPrompt);
    if (!recognizer->initial_prompt.empty()) {
        // Counted once here purely for last_pass_n_batchd; whisper_full
        // re-tokenizes initial_prompt itself on every pass that uses it.
        recognizer->initial_prompt_n_tokens =
                whisper_token_count(model->ctx, recognizer->initial_prompt.c_str());
    }
    recognizer->no_speech_threshold = noSpeechThreshold;
    recognizer->endpoint_silence_ms = endpointSilenceMs;
    recognizer->utterance_end_pad_ms = std::min(UTTERANCE_END_PAD_MS, endpointSilenceMs / 2);
    recognizer->max_utterance_ms = maxUtteranceMs;
    recognizer->max_context_tokens = std::max(1, whisper_n_text_ctx(model->ctx) / 2);

    jlong handle;
    {
        std::lock_guard<std::mutex> lock(whisperRecognizerInstancesMutex);
        handle = ++whisperRecognizerInstanceCounter;
        whisperRecognizerInstances[handle] = std::move(recognizer);
    }
    return handle;
}


JNIEXPORT void JNICALL Java_app_versta_translate_bridge_whisper_Whisper_destroy(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    std::unique_ptr<Recognizer> recognizer;
    {
        std::lock_guard<std::mutex> lock(whisperRecognizerInstancesMutex);
        auto it = whisperRecognizerInstances.find(handle);
        if (it == whisperRecognizerInstances.end()) {
            return;
        }
        recognizer = std::move(it->second);
        whisperRecognizerInstances.erase(it);
    }
    {
        std::lock_guard<std::mutex> lock(recognizer->mtx);
        recognizer->pending.clear();
    }
    if (recognizer->callback_obj != nullptr) {
        env->DeleteGlobalRef(recognizer->callback_obj);
        recognizer->callback_obj = nullptr;
    }
}


JNIEXPORT void JNICALL Java_app_versta_translate_bridge_whisper_Whisper_setCallback(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject callback
) {
    auto recognizer = findRecognizer(handle);
    if (recognizer == nullptr) {
        return;
    }


    // Release the previous callback global ref using the current thread's env.
    if (recognizer->callback_obj != nullptr) {
        env->DeleteGlobalRef(recognizer->callback_obj);
        recognizer->callback_obj = nullptr;
    }


    recognizer->callback_obj = env->NewGlobalRef(callback);
    jclass clazz = env->GetObjectClass(callback);
    // isFinal is gone: every emitted segment is now a completed utterance,
    // there is no provisional/partial path. The int[] carries this
    // utterance's decoded token ids for Kotlin to persist as context (see
    // Recognizer::carried_prompt_ids) — empty when the decode did not pass
    // the quality gate.
    recognizer->callback_method = env->GetMethodID(
            clazz, "onSegment", "(Ljava/lang/String;JJ[I)V");
    env->DeleteLocalRef(clazz);
}


JNIEXPORT void JNICALL Java_app_versta_translate_bridge_whisper_Whisper_feed(
        JNIEnv *env,
        jobject,
        jlong handle,
        jfloatArray pcm,
        jint nSamples
) {
    if (pcm == nullptr || nSamples <= 0) {
        return;
    }
    auto recognizer = findRecognizer(handle);
    if (recognizer == nullptr) {
        return;
    }
    const jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    if (data == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(recognizer->mtx);
        recognizer->pending.insert(recognizer->pending.end(), data, data + nSamples);
        // Cap pending to avoid unbounded growth if process() goes unpolled —
        // see MAX_PENDING_SLACK_MS.
        const size_t cap =
                static_cast<size_t>(recognizer->max_utterance_ms + MAX_PENDING_SLACK_MS) *
                WHISPER_SAMPLE_RATE / 1000;
        if (recognizer->pending.size() > cap) {
            const size_t drop = recognizer->pending.size() - cap;
            recognizer->pending.erase(
                    recognizer->pending.begin(),
                    recognizer->pending.begin() + static_cast<std::deque<float>::difference_type>(drop));
            // advance_front (not a bare consumed_samples += drop) because
            // this can truncate a live utterance: without re-anchoring
            // speech_start_ms, the next poll would read !outstanding and
            // trim away whatever of the sentence survived.
            advance_front(recognizer, drop);
        }
    }
    env->ReleaseFloatArrayElements(pcm, const_cast<jfloat *>(data), JNI_ABORT);
}


JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_whisper_Whisper_setCarriedContext(
        JNIEnv *env,
        jobject,
        jlong handle,
        jintArray tokens
) {
    auto rec = findRecognizer(handle);
    if (rec == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(rec->mtx);
    rec->carried_prompt_ids.clear();
    if (tokens == nullptr) {
        return;
    }
    const jsize n = env->GetArrayLength(tokens);
    if (n <= 0) {
        return;
    }
    jint *data = env->GetIntArrayElements(tokens, nullptr);
    if (data == nullptr) {
        return;
    }
    rec->carried_prompt_ids.assign(data, data + n);
    if (rec->carried_prompt_ids.size() > static_cast<size_t>(rec->max_context_tokens)) {
        rec->carried_prompt_ids.erase(
                rec->carried_prompt_ids.begin(),
                rec->carried_prompt_ids.end() - rec->max_context_tokens);
    }
    env->ReleaseIntArrayElements(tokens, data, JNI_ABORT);
}


JNIEXPORT void JNICALL Java_app_versta_translate_bridge_whisper_Whisper_reset(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto recognizer = findRecognizer(handle);
    if (recognizer == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(recognizer->mtx);
        recognizer->pending.clear();
        recognizer->consumed_samples = 0;
        recognizer->speech_until_ms = 0;
        recognizer->speech_start_ms = -1;
        recognizer->probed_until_ms = 0;
        // Cross-session carry is Kotlin's responsibility (SpeechContextStore)
        // — see the field comment on carried_prompt_ids.
        recognizer->carried_prompt_ids.clear();
        recognizer->language = recognizer->language_initial;
        recognizer->language_auto = recognizer->language_initial.empty();
        // Cost estimates are device+model specific but the model can change
        // between sessions, so re-measure rather than carry stale figures over.
        recognizer->commit_rate_ema_ms_per_sec = 0.0;
        // Reset per-session counters so RTF starts fresh for the next
        // session; keep last-pass snapshot so the caller can still inspect
        // the final pass of the previous session if needed.
        recognizer->pass_count = 0;
        recognizer->abort_count = 0;
        recognizer->vad_skip_count = 0;
        recognizer->processed_audio_sec = 0.0;
        recognizer->commit_compute_ms = 0.0;
        recognizer->have_last_pass = false;
        // No explicit whisper_vad_reset_state() call needed: every VAD
        // invocation now goes through whisper_vad_segments_from_samples,
        // which resets the LSTM state itself before scanning (via
        // whisper_vad_detect_speech) — there is no persistent VAD state left
        // for this recognizer to own.
    }
}


// Abort callback for whisper_full. Called per decoder step and after encoder
// graph compute. Returns true once the wall-clock deadline has passed, which
// causes whisper_full to return -6 or -8 (non-zero) and bail out of the
// temperature-fallback decode loop.
static bool whisper_abort_cb(void *data) {
    auto *rec = static_cast<Recognizer *>(data);
    return std::chrono::steady_clock::now() >= rec->abort_deadline;
}


// Find the index (in token-id space) at which a degenerate repetition loop
// begins, keeping the first cycle of the loop and truncating the rest.
// Whisper's own temperature fallback cannot rescue these: this vendored
// version only retries on avg_logprob below threshold, and confident loops
// have high logprobs. Returns n when no repetition is found.
//
// Detection: any token n-gram (len >= 4) repeated at least 3 times
// consecutively. Natural speech essentially never does this; a model stuck in
// a loop does it every time.
static size_t repetition_truncate_index(const std::vector<int> &ids) {
    const size_t n = ids.size();
    if (n < 12) {
        return n;
    }
    size_t truncate = n;
    for (size_t unit = 4; unit <= n / 3; ++unit) {
        for (size_t start = 0; start + 3 * unit <= n; ++start) {
            bool repeat = true;
            for (size_t k = 0; k < unit && repeat; ++k) {
                repeat = ids[start + k] == ids[start + unit + k] &&
                                  ids[start + k] == ids[start + 2 * unit + k];
            }
            if (repeat) {
                truncate = std::min(truncate, start + unit);
                break;  // earliest start wins for this unit size
            }
        }
    }
    return truncate;
}


// What one cheap VAD probe reports over the samples it was given. All times
// are ms offsets relative to the START of those samples. -1 means "the
// scanned window held no such frame" — NOT "no such frame exists anywhere":
// the caller is responsible for scanning back far enough that "-1" can be
// treated as a real answer (see Recognizer::probed_until_ms).
struct TrailingProbe {
    // false => the VAD call itself failed. Every other field is meaningless
    // in that case, and the caller must leave its watermarks untouched.
    bool valid = false;

    // End of the last frame above `threshold`. Drives the endpoint
    // watermark (Recognizer::speech_until_ms) — deliberately the STRICT
    // threshold, so a marginal frame delays the endpoint rather than
    // triggering one early.
    int64_t last_speech_end_ms = -1;

    // Start of the first run of SPEECH_ONSET_FRAMES consecutive frames above
    // `threshold`. Only meaningful when the true onset lies inside this
    // scan; the caller latches it once into Recognizer::speech_start_ms and
    // never recomputes.
    int64_t first_speech_start_ms = -1;

    // End of the last frame above `threshold - VAD_HYSTERESIS`. Always >=
    // last_speech_end_ms. This, not last_speech_end_ms, is the right window
    // END bound — it is what whisper.cpp's own segmenter would have used to
    // close a segment here.
    int64_t last_voiced_end_ms = -1;

    // Midpoint of the most recent run of >= CAP_CUT_MIN_PAUSE_MS consecutive
    // sub-hysteresis frames: the least damaging place to cut an utterance
    // that has hit the length cap. -1 => no run long enough was seen in this
    // scan.
    int64_t last_pause_mid_ms = -1;
};


// Cheap VAD probe over `samples` — the caller has already sliced this to
// exactly the region needing scanning (see process()). Reports four signals
// from one pass over the frame probabilities rather than one; replaces an
// earlier design that additionally ran a full whisper.cpp segmentation
// (whisper_vad_segments_from_samples) once a boundary looked likely. That
// two-stage design could disagree with itself — the segmenter's own notion
// of "ready" is measured on VAD-trimmed groups, not the raw buffer the probe
// reasons about — and on continuous speech at the length cap that
// disagreement dropped a full utterance's audio. See the constants above.
static TrailingProbe probe_speech(whisper_vad_context *vctx,
                                   const float *samples, size_t n_samples,
                                   float threshold) {
    TrailingProbe out;
    if (vctx == nullptr || samples == nullptr || n_samples == 0) {
        return out;
    }
    // Stateless (state-resetting) variant: successive polls re-scan
    // overlapping windows, which would corrupt the LSTM continuity the
    // _no_reset variant assumes.
    if (!whisper_vad_detect_speech(vctx, samples, static_cast<int>(n_samples))) {
        return out;
    }
    const int n_probs = whisper_vad_n_probs(vctx);
    const float *probs = whisper_vad_probs(vctx);
    if (n_probs <= 0 || probs == nullptr) {
        return out;
    }
    out.valid = true;

    // Frames evenly divide the scanned slice; derive the stride rather than
    // hardcoding the Silero window size so this stays correct if the VAD
    // model changes.
    const double frame_ms =
            static_cast<double>(n_samples) / n_probs * 1000.0 / WHISPER_SAMPLE_RATE;
    const auto frame_start = [&](int i) { return static_cast<int64_t>(i * frame_ms); };
    const auto frame_end = [&](int i) { return static_cast<int64_t>((i + 1) * frame_ms); };

    float neg = threshold - VAD_HYSTERESIS;
    if (neg < 0.01f) neg = 0.01f;
    const int min_pause_frames =
            std::max(1, static_cast<int>(std::ceil(CAP_CUT_MIN_PAUSE_MS / frame_ms)));

    int hi_run = 0;             // length of the current above-threshold run
    int pause_run_start = -1;   // index the current sub-hysteresis run began at

    for (int i = 0; i < n_probs; ++i) {
        const float p = probs[i];

        if (p > threshold) {
            ++hi_run;
            if (hi_run >= SPEECH_ONSET_FRAMES) {
                // Same onset-run requirement as first_speech_start_ms below:
                // whisper_vad_detect_speech resets the Silero LSTM on every
                // call, so a lone frame near the start of a scan is a
                // warm-up transient, not real speech. Without this, one
                // spurious frame in a silent room latches speech_start_ms
                // and process() decodes a silence window 600ms later.
                out.last_speech_end_ms = frame_end(i);
                if (out.first_speech_start_ms < 0) {
                    out.first_speech_start_ms = frame_start(i - (SPEECH_ONSET_FRAMES - 1));
                }
            }
        } else {
            hi_run = 0;
        }

        if (p > neg) {
            out.last_voiced_end_ms = frame_end(i);
            pause_run_start = -1;  // pause broken
        } else {
            if (pause_run_start < 0) {
                pause_run_start = i;
            }
            if (i - pause_run_start + 1 >= min_pause_frames) {
                // Keep extending: the LAST qualifying pause wins. Midpoint,
                // so a cut there leaves the trailing consonant with this
                // utterance and a lead-in for whatever follows.
                out.last_pause_mid_ms =
                        (frame_start(pause_run_start) + frame_end(i)) / 2;
            }
        }
    }
    return out;
}


// This poll's answers only (never persisted) from the probe_speech() call
// probe_and_update() just folded into the Recognizer's watermarks.
struct ProbeUpdate {
    bool valid = false;
    int64_t voiced_end_ms = -1;
    int64_t pause_mid_ms = -1;
};

// Scans every sample not yet scored (back to probed_until_ms, or the usual
// trailing lookback if that is more recent) and folds the result into
// rec->speech_until_ms / rec->speech_start_ms / rec->probed_until_ms. Shared
// by process() and flush() so "is speech outstanding" is answered identically
// by both — see the comment above TrailingProbe for why divergence here is
// exactly the defect this design removes. Caller must hold rec->mtx.
static ProbeUpdate probe_and_update(Recognizer *rec, int64_t t_front_ms,
                                     int64_t buffer_end_ms) {
    ProbeUpdate out;

    int64_t scan_from_ms =
            std::min(buffer_end_ms - ENDPOINT_SCAN_WINDOW_MS, rec->probed_until_ms);
    scan_from_ms = std::max(scan_from_ms, t_front_ms);
    const size_t scan_from = static_cast<size_t>(scan_from_ms - t_front_ms) *
            WHISPER_SAMPLE_RATE / 1000;

    std::vector<float> scan(
            rec->pending.begin() +
                    static_cast<std::deque<float>::difference_type>(scan_from),
            rec->pending.end());
    const TrailingProbe p =
            probe_speech(rec->vctx, scan.data(), scan.size(), rec->vad_threshold);
    if (!p.valid) {
        // Leave every watermark (including probed_until_ms) untouched — the
        // next call re-scans this same region rather than silently treating
        // a failed VAD call as silence.
        return out;
    }

    out.valid = true;
    rec->probed_until_ms = buffer_end_ms;
    const int64_t base_ms = t_front_ms +
            static_cast<int64_t>(scan_from) * 1000 / WHISPER_SAMPLE_RATE;

    if (p.last_speech_end_ms >= 0) {
        rec->speech_until_ms =
                std::max(rec->speech_until_ms, base_ms + p.last_speech_end_ms);
        if (rec->speech_start_ms < 0) {
            // Latch once, on the call that first makes speech outstanding —
            // see the invariant on speech_start_ms. Prefer the run-confirmed
            // onset; fall back to the last-speech frame so the latch can
            // never be skipped.
            const int64_t onset = p.first_speech_start_ms >= 0
                    ? base_ms + p.first_speech_start_ms
                    : base_ms + p.last_speech_end_ms;
            rec->speech_start_ms = std::max(t_front_ms, onset);
        }
    }
    if (p.last_voiced_end_ms >= 0) {
        out.voiced_end_ms = base_ms + p.last_voiced_end_ms;
    }
    if (p.last_pause_mid_ms >= 0) {
        out.pause_mid_ms = base_ms + p.last_pause_mid_ms;
    }
    return out;
}


// Decode exactly one utterance in a single whisper_full call. If the result
// passes the post-decode quality gates, emits it via the Java callback and
// updates rec->carried_prompt_ids so the NEXT utterance in this session is
// automatically primed with it. window_start_ms is this utterance's absolute
// stream-time start, used only for the callback's start/end timestamps.
//
// Always consumes its wall-clock/metrics bookkeeping regardless of outcome;
// the caller (process()/flush()) is responsible for removing the
// corresponding samples from `pending`.
static void transcribe_utterance(Recognizer *rec, JNIEnv *env,
                                  const float *pcm, size_t n_samples,
                                  int64_t window_start_ms, bool is_flush) {
    if (n_samples == 0) {
        return;
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads = rec->n_threads;
    wparams.single_segment = true;
    wparams.no_timestamps = true;
    wparams.no_context = true;
    wparams.translate = false;
    wparams.language = rec->language.empty() ? "auto" : rec->language.c_str();

    // Context priming: the previous utterance's own decoded tokens (if any —
    // see the carry update below for when that is trusted) take precedence
    // over the static per-language prompt. whisper_full itself prefers
    // prompt_tokens over initial_prompt whenever both are set
    // (whisper.cpp's "prepare prompt" step), so no explicit branching on
    // which one to send is needed beyond this if/else.
    if (!rec->carried_prompt_ids.empty()) {
        wparams.prompt_tokens = rec->carried_prompt_ids.data();
        wparams.prompt_n_tokens = static_cast<int>(rec->carried_prompt_ids.size());
    } else if (!rec->initial_prompt.empty()) {
        wparams.initial_prompt = rec->initial_prompt.c_str();
    }

    wparams.suppress_nst = true;
    wparams.max_tokens = std::max(96, static_cast<int>(n_samples * 12 / WHISPER_SAMPLE_RATE));
    // Temperature fallback is disabled outright, on every pass.
    //
    // With temperature_inc > 0 whisper builds a ladder of temperatures and, at
    // every rung above zero, runs greedy.best_of (5) decoder sequences instead
    // of one — a worst case of 1 + 5 + 5 = 11 decodes per call. The trigger is
    // `decoder.failed` (entropy gate or repetition-loop flag), which ignores
    // no_speech_prob entirely, so music and other non-speech reliably burn the
    // whole ladder. The first fallback also permanently reallocates the
    // self-attention KV cache at best_of + 2.
    //
    // What this gives up is whisper's own escape from repetition loops. That
    // is already covered post-hoc by repetition_truncate_index, and
    // single_segment + max_tokens bound a runaway sequence, so the worst case
    // here is one sequence running to max_tokens rather than a multi-second
    // cascade.
    wparams.temperature_inc = 0.0f;
    wparams.no_speech_thold = 0.6f;

    const int mel_frames = static_cast<int>(n_samples / 160);
    int audio_ctx = mel_frames / 2 + 32;
    if (audio_ctx < 256) audio_ctx = 256;
    if (audio_ctx > 1500) audio_ctx = 1500;
    wparams.audio_ctx = audio_ctx;

    const long window_ms = static_cast<long>(n_samples) * 1000 / WHISPER_SAMPLE_RATE;
    const double window_sec = static_cast<double>(window_ms) / 1000.0;
    // Self-tuning from the measured decode rate, falling back to a
    // deliberately pessimistic cold-start rate rather than to a flat floor —
    // see the constants above for why a flat floor was fatal for long
    // utterances.
    long budget_ms = static_cast<long>(
            (rec->commit_rate_ema_ms_per_sec > 0.0
                     ? COMMIT_BUDGET_FACTOR * rec->commit_rate_ema_ms_per_sec
                     : COMMIT_BUDGET_COLD_RATE_MS_PER_SEC) * window_sec);
    if (budget_ms < COMMIT_BUDGET_FLOOR_MS) budget_ms = COMMIT_BUDGET_FLOOR_MS;
    const long ceiling_ms =
            std::max<long>(COMMIT_BUDGET_CEILING_MS,
                            static_cast<long>(COMMIT_BUDGET_MAX_RATIO * static_cast<double>(window_ms)));
    if (budget_ms > ceiling_ms) budget_ms = ceiling_ms;
    rec->abort_deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(budget_ms);
    wparams.abort_callback = whisper_abort_cb;
    wparams.abort_callback_user_data = rec;

    const auto t0 = std::chrono::steady_clock::now();
    whisper_reset_timings(rec->ctx);
    const int ret = whisper_full(rec->ctx, wparams, pcm, static_cast<int>(n_samples));
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - t0).count();

    // Snapshot last-pass metrics while the timings are still valid (the next
    // transcribe_utterance call will whisper_reset_timings again).
    rec->last_pass_elapsed_ms = elapsed;
    rec->last_pass_window_ms = window_ms;
    rec->last_pass_n_samples = n_samples;
    rec->last_pass_audio_ctx = audio_ctx;
    rec->last_pass_max_tokens = wparams.max_tokens;
    rec->last_pass_budget_ms = budget_ms;
    rec->last_pass_ret = ret;
    rec->last_pass_was_flush = is_flush;
    {
        const whisper_timings *t = whisper_get_timings(rec->ctx);
        // Null when ctx->state never initialized (e.g. a pass that failed
        // before the encoder ran).
        if (t != nullptr) {
            rec->last_pass_encode_ms = t->encode_ms;
            rec->last_pass_decode_ms = t->decode_ms;
            rec->last_pass_batchd_ms = t->batchd_ms;
            delete t;
        }
    }
    rec->last_pass_n_encode = 1 + (rec->language_auto ? 1 : 0);
    const int n_seg_for_metrics = ret == 0 ? whisper_full_n_segments(rec->ctx) : 0;
    int n_decode = rec->language_auto ? 1 : 0;
    for (int i = 0; i < n_seg_for_metrics; ++i) {
        n_decode += whisper_full_n_tokens(rec->ctx, i);
    }
    rec->last_pass_n_decode = n_decode;
    rec->last_pass_n_batchd = whisper_is_multilingual(rec->ctx) ? 4 : 2;
    if (!rec->carried_prompt_ids.empty()) {
        rec->last_pass_n_batchd += static_cast<int>(rec->carried_prompt_ids.size()) + 1;
    } else if (rec->initial_prompt_n_tokens > 0) {
        rec->last_pass_n_batchd += rec->initial_prompt_n_tokens + 1;
    }
    rec->have_last_pass = true;

    rec->pass_count++;
    if (window_ms > 0) {
        const double observed = static_cast<double>(elapsed) / window_sec;
        if (ret == 0) {
            rec->commit_rate_ema_ms_per_sec =
                    rec->commit_rate_ema_ms_per_sec <= 0.0
                            ? observed
                            : COMMIT_EMA_ALPHA * observed +
                                        (1.0 - COMMIT_EMA_ALPHA) * rec->commit_rate_ema_ms_per_sec;
        } else if (observed > rec->commit_rate_ema_ms_per_sec) {
            // An aborted pass does not measure the true decode rate, but it
            // does establish a LOWER BOUND on it — and adopting that bound is
            // what makes an abort recoverable. The budget is derived from
            // this estimate, so leaving it untouched on abort means a pass
            // that times out once times out identically forever, dropping its
            // audio every time. Raising it guarantees the next attempt gets a
            // strictly larger budget.
            rec->commit_rate_ema_ms_per_sec = observed;
        }
    }
    rec->commit_compute_ms += static_cast<double>(elapsed);
    // Charged here, beside the compute it is the RTF numerator for — not at
    // the call sites, which also covered dropped and VAD-trimmed audio and
    // so counted audio against ~zero compute. That inflated RTF exactly when
    // the pipeline was dropping the most, and helped mask the bug this
    // rewrite fixes.
    rec->processed_audio_sec += static_cast<double>(n_samples) / WHISPER_SAMPLE_RATE;

    if (ret != 0) {
        // Aborted: whatever made this undecodable will make it undecodable
        // again. Nothing to emit, and don't touch the carry — a bad
        // utterance should not poison a good standing context for the next
        // one (see the carried_prompt_ids field comment).
        rec->abort_count++;
        return;
    }

    if (rec->language_auto) {
        const int lang_id = whisper_full_lang_id(rec->ctx);
        if (lang_id >= 0) {
            const char *lang_str = whisper_lang_str(lang_id);
            if (lang_str != nullptr && lang_str[0] != '\0') {
                rec->language = lang_str;
                rec->language_auto = false;
            }
        }
    }

    const int n_segments = whisper_full_n_segments(rec->ctx);
    if (n_segments <= 0) {
        return;  // decoder produced nothing; nothing to emit or carry
    }

    // Post-decode non-speech gate. whisper suppresses a segment internally
    // only when no_speech_prob is high AND avg_logprob is low, so a
    // *confident* hallucination on music is emitted regardless. Reading the
    // probability costs nothing — the decode already happened.
    float no_speech_prob = 0.0f;
    for (int i = 0; i < n_segments; ++i) {
        no_speech_prob =
                std::max(no_speech_prob, whisper_full_get_segment_no_speech_prob(rec->ctx, i));
    }
    if (no_speech_prob > rec->no_speech_threshold) {
        return;
    }

    // Flatten decoded tokens (single_segment => normally one segment),
    // excluding special tokens (id >= eot: EOT, SOT, PREV, lang, task,
    // timestamp tokens) — never part of the user-facing transcript, and
    // exactly the tokens worth carrying forward as context.
    const int eot_id = whisper_token_eot(rec->ctx);
    struct TokTextInfo {
        whisper_token id;
        const char *text;
        float plog;
    };
    std::vector<TokTextInfo> toks;
    for (int i = 0; i < n_segments; ++i) {
        const int n_tok = whisper_full_n_tokens(rec->ctx, i);
        for (int j = 0; j < n_tok; ++j) {
            const whisper_token_data td = whisper_full_get_token_data(rec->ctx, i, j);
            if (static_cast<int>(td.id) >= eot_id) {
                continue;
            }
            toks.push_back({static_cast<whisper_token>(td.id),
                             whisper_full_get_token_text(rec->ctx, i, j), td.plog});
        }
    }
    if (toks.empty()) {
        return;
    }

    // Repetition-loop guard: keep only the first cycle. Whisper's own
    // temperature fallback cannot rescue these (see the comment on
    // temperature_inc above) — this is the post-hoc backstop.
    std::vector<int> tokens;
    tokens.reserve(toks.size());
    for (const auto &t : toks) {
        tokens.push_back(t.id);
    }
    const size_t truncate = repetition_truncate_index(tokens);
    const bool looped = truncate < toks.size();

    std::string text;
    double sum_plog = 0.0;
    std::vector<whisper_token> ids;
    ids.reserve(truncate);
    for (size_t i = 0; i < truncate; ++i) {
        if (toks[i].text != nullptr) {
            text += toks[i].text;
        }
        sum_plog += toks[i].plog;
        ids.push_back(toks[i].id);
    }

    // Normalize whitespace (whisper tokens carry their own leading space).
    const size_t b = text.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) {
        return;  // nothing but whitespace
    }
    const size_t e = text.find_last_not_of(" \t\r\n");
    text = text.substr(b, e - b + 1);
    if (text.empty()) {
        return;
    }

    // Carry quality gate: an utterance that looped or decoded with low
    // average confidence should not prime the next one. Its TEXT is still
    // reported to Kotlin either way (the user still gets to see/hear
    // whatever was transcribed) — only the context-carry side effect is
    // suppressed, leaving whatever context was already standing intact for
    // the utterance after this one.
    const double avg_logprob =
            !ids.empty() ? sum_plog / static_cast<double>(ids.size()) : 0.0;
    const bool trustworthy_for_carry = !looped && avg_logprob >= CARRY_LOGPROB_THRESHOLD;
    if (trustworthy_for_carry) {
        if (ids.size() > static_cast<size_t>(rec->max_context_tokens)) {
            ids.erase(ids.begin(), ids.end() - rec->max_context_tokens);
        }
        rec->carried_prompt_ids = ids;
    }

    if (rec->callback_obj == nullptr || rec->callback_method == nullptr) {
        return;
    }
    const int64_t end_ms = window_start_ms + window_ms;
    jstring jtext = env->NewStringUTF(text.c_str());
    jintArray jids = env->NewIntArray(trustworthy_for_carry ? static_cast<jsize>(ids.size()) : 0);
    if (jids != nullptr && trustworthy_for_carry && !ids.empty()) {
        env->SetIntArrayRegion(jids, 0, static_cast<jsize>(ids.size()), ids.data());
    }
    env->CallVoidMethod(rec->callback_obj, rec->callback_method, jtext,
                         static_cast<jlong>(window_start_ms), static_cast<jlong>(end_ms), jids);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (jtext != nullptr) {
        env->DeleteLocalRef(jtext);
    }
    if (jids != nullptr) {
        env->DeleteLocalRef(jids);
    }
}


// Check buffered audio for a complete utterance and, if one is ready,
// transcribe it in a single whisper_full call and emit it via callback.
//
// Stream timeline: consumed_samples is the stream position of pending.front().
// Unlike the old streaming design there is no seam to advance — each
// utterance is decoded exactly once and its audio is simply dropped from
// `pending` afterwards.
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_whisper_Whisper_process(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto rec = findRecognizer(handle);
    if (rec == nullptr) {
        return 0;
    }

    std::unique_lock<std::mutex> lock(rec->mtx);

    if (!rec->vad_enabled || rec->vctx == nullptr) {
        // No way to auto-detect utterance boundaries in this mode; only
        // flush() (on stop()) ever emits anything.
        return 0;
    }
    if (rec->pending.empty()) {
        return 0;
    }

    const int64_t t_front_ms = rec->consumed_samples * 1000 / WHISPER_SAMPLE_RATE;
    const size_t n_pending = rec->pending.size();
    const int64_t buffer_end_ms =
            t_front_ms + static_cast<int64_t>(n_pending) * 1000 / WHISPER_SAMPLE_RATE;

    // Scan far enough back to cover every sample never yet scored
    // (probed_until_ms), plus the usual trailing lookback. In steady state
    // probed_until_ms sits within one poll of buffer_end_ms, so this picks
    // the fixed ENDPOINT_SCAN_WINDOW_MS window and costs exactly what a
    // fixed-window probe always cost. After a stall (a long decode held the
    // pipeline while feed() kept appending) it picks probed_until_ms instead
    // and pays a one-off catch-up scan, bounded by feed()'s own
    // MAX_PENDING_SLACK_MS cap — without this, audio the probe never
    // actually saw would read as silence and become droppable.
    const ProbeUpdate probe = probe_and_update(rec, t_front_ms, buffer_end_ms);
    const int64_t voiced_end_ms = probe.voiced_end_ms;
    const int64_t pause_mid_ms = probe.pause_mid_ms;

    const bool outstanding = rec->speech_until_ms > t_front_ms;

    if (!outstanding) {
        // Nothing buffered is speech. Trim back to a bounded trailing window
        // so a long silence does not grow `pending` without bound — anything
        // older is silence by construction (the watermark would be ahead of
        // t_front_ms otherwise; only advance_front clears it, and only when
        // speech_until_ms <= front_ms).
        rec->vad_skip_count++;
        const size_t keep_samples =
                static_cast<size_t>(ENDPOINT_SCAN_WINDOW_MS) * WHISPER_SAMPLE_RATE / 1000;
        if (n_pending > keep_samples) {
            const size_t drop = n_pending - keep_samples;
            rec->pending.erase(rec->pending.begin(),
                                rec->pending.begin() +
                                        static_cast<std::deque<float>::difference_type>(drop));
            advance_front(rec, drop);
            return static_cast<jlong>(drop);
        }
        return 0;
    }

    // Speech is outstanding. win_start is anchored to the LATCHED onset:
    // frozen for as long as this utterance keeps accumulating, regardless of
    // how far the buffer grows underneath it.
    const int64_t win_start_ms =
            std::max(t_front_ms, rec->speech_start_ms - VAD_SPEECH_PAD_MS);

    const int64_t silence_since_speech_ms = buffer_end_ms - rec->speech_until_ms;
    const bool endpoint_reached = silence_since_speech_ms >= rec->endpoint_silence_ms;
    // Measured on the WINDOW that would actually be cut, not the raw
    // buffer: a cap condition and a cut applied to two different quantities
    // is exactly the defect this rewrite removes (see the comment above
    // TrailingProbe). Tested only once win_start is known, and after
    // endpoint_reached — if both hold, the endpoint bound is the better one.
    const bool hit_cap =
            !endpoint_reached && (buffer_end_ms - win_start_ms) >= rec->max_utterance_ms;

    if (!endpoint_reached && !hit_cap) {
        // Speech continuing, no boundary yet. win_start stays latched while
        // buffer_end_ms grows monotonically with feed(), so this reaches
        // either a real pause (endpoint) or the cap within bounded
        // wall-clock — never a spin.
        return 0;
    }

    int64_t win_end_ms;
    if (endpoint_reached) {
        // Prefer the hysteresis-closed end over the strict watermark — this
        // is what whisper.cpp's own segmenter would have closed the segment
        // at, and it is provably safe: this region is already covered by
        // confirmed silence (silence_since_speech_ms >= endpoint_silence_ms),
        // so the pad can never reach into a following utterance.
        const int64_t speech_end_ms = std::max(rec->speech_until_ms, voiced_end_ms);
        win_end_ms = std::min(buffer_end_ms, speech_end_ms + rec->utterance_end_pad_ms);
    } else {
        // Cap path: cut at the hard limit unless a real pause sits in the
        // trailing window this same poll just scanned — cutting there avoids
        // slicing mid-word. Bounded so a stale or out-of-range pause (from
        // scanning before this window existed, or too far back to vouch for)
        // cannot be used.
        win_end_ms = win_start_ms + rec->max_utterance_ms;
        if (pause_mid_ms > win_end_ms - ENDPOINT_SCAN_WINDOW_MS &&
                pause_mid_ms < win_end_ms) {
            win_end_ms = pause_mid_ms;
        }
    }
    // The guarantee: every branch above only PROPOSES an end bound; this
    // unconditional clamp is what makes "no window ever exceeds
    // max_utterance_ms" true by construction rather than by argument over
    // the branches. Matters most for the endpoint branch — a slow poll can
    // leave a buffer longer than the cap with a legitimate endpoint at its
    // far end.
    win_end_ms = std::min(win_end_ms, win_start_ms + rec->max_utterance_ms);

    const size_t start_sample =
            static_cast<size_t>(win_start_ms - t_front_ms) * WHISPER_SAMPLE_RATE / 1000;
    const size_t end_sample = std::min(
            n_pending,
            static_cast<size_t>(win_end_ms - t_front_ms) * WHISPER_SAMPLE_RATE / 1000);

    std::vector<float> utterance;
    if (end_sample > start_sample) {
        utterance.assign(
                rec->pending.begin() +
                        static_cast<std::deque<float>::difference_type>(start_sample),
                rec->pending.begin() +
                        static_cast<std::deque<float>::difference_type>(end_sample));
    }

    // Consume everything up to the end of this window from `pending` — both
    // the utterance audio itself and any leading silence before win_start.
    // Nothing in this range is ever re-examined: an utterance is decoded
    // exactly once (or, below MIN_TRANSCRIBE_WINDOW_MS, deliberately not
    // decoded at all — but still consumed, so the buffer always makes
    // progress once a boundary has been decided).
    rec->pending.erase(rec->pending.begin(),
                        rec->pending.begin() +
                                static_cast<std::deque<float>::difference_type>(end_sample));
    advance_front(rec, end_sample);

    if (utterance.size() < static_cast<size_t>(MIN_TRANSCRIBE_WINDOW_MS) *
            WHISPER_SAMPLE_RATE / 1000) {
        // Empty/inverted (defensive — should not happen given the arithmetic
        // above) or too short to hold a syllable worth a decode pass. Either
        // way, progress was already made by the consume above.
        rec->vad_skip_count++;
        return static_cast<jlong>(end_sample);
    }

    // Release the lock before transcription (long operation — feed() needs it).
    lock.unlock();
    transcribe_utterance(rec, env, utterance.data(), utterance.size(),
                          win_start_ms, /*is_flush=*/false);

    return static_cast<jlong>(end_sample);
}


// Drain ALL remaining pending audio and transcribe it, regardless of trailing
// silence confirmation — there is no "next pass" left to wait for it in.
// Called on stop() so the user's last utterance is not silently dropped.
//
// Loops rather than a single call so a backlog longer than one utterance
// (process() fell behind, or the app was backgrounded) is still fully
// drained instead of silently truncated to whisper's ~30s context limit.
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_whisper_Whisper_flush(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto rec = findRecognizer(handle);
    if (rec == nullptr) {
        return 0;
    }

    std::unique_lock<std::mutex> lock(rec->mtx);
    if (rec->pending.empty()) {
        return 0;
    }

    const int64_t t_front_ms = rec->consumed_samples * 1000 / WHISPER_SAMPLE_RATE;
    const size_t n_pending = rec->pending.size();
    const int64_t buffer_end_ms =
            t_front_ms + static_cast<int64_t>(n_pending) * 1000 / WHISPER_SAMPLE_RATE;

    // Defaults to the whole buffer: VAD-less mode (vad_enabled == false, or
    // no vctx) has no boundary detector at all, so flush() is the only
    // output path and must stay unconditional, exactly like before this
    // gate was added.
    int64_t win_start_ms = t_front_ms;
    int64_t win_end_ms = buffer_end_ms;
    bool discard = false;  // true => consume this buffer without decoding it
    bool gated = false;    // true => the MIN_TRANSCRIBE_WINDOW_MS floor below applies

    if (rec->vad_enabled && rec->vctx != nullptr) {
        // Run the same probe process() uses so "is speech outstanding" can
        // never disagree between the two call sites. Without this, flush()
        // unconditionally decoded the ~ENDPOINT_SCAN_WINDOW_MS of trailing
        // silence process() always leaves buffered (see its !outstanding
        // trim path) on every single stop() — the source of silence
        // hallucinations.
        const ProbeUpdate probe = probe_and_update(rec, t_front_ms, buffer_end_ms);
        if (probe.valid) {
            gated = true;
            const bool outstanding = rec->speech_until_ms > t_front_ms;
            if (!outstanding) {
                discard = true;
            } else {
                // No endpoint confirmation to lean on here (unlike
                // process()) — if the user was still talking when stop()
                // landed, speech_until_ms sits at buffer_end_ms and win_end
                // clamps there too, so nothing is lost.
                win_start_ms = std::max(t_front_ms, rec->speech_start_ms - VAD_SPEECH_PAD_MS);
                const int64_t speech_end_ms =
                        std::max(rec->speech_until_ms, probe.voiced_end_ms);
                win_end_ms = std::min(buffer_end_ms, speech_end_ms + rec->utterance_end_pad_ms);
                if (rec->speech_until_ms - rec->speech_start_ms < MIN_SPEECH_MS) {
                    // Watermark span, not a probe-local count: probe only
                    // scans the catch-up region, so a probe-local total
                    // would under-report a long utterance. This is content
                    // within the window, orthogonal to the length floor
                    // below — catches an impulsive click that still clears
                    // SPEECH_ONSET_FRAMES.
                    discard = true;
                }
            }
        }
        // probe.valid == false: the VAD call itself failed. Fall through
        // with the whole-buffer window rather than risk treating a failed
        // probe as silence and dropping real speech.
    }

    std::vector<float> buf;
    if (!discard) {
        const size_t start_sample =
                static_cast<size_t>(win_start_ms - t_front_ms) * WHISPER_SAMPLE_RATE / 1000;
        const size_t end_sample = std::min(
                n_pending,
                static_cast<size_t>(win_end_ms - t_front_ms) * WHISPER_SAMPLE_RATE / 1000);
        if (end_sample > start_sample) {
            buf.assign(
                    rec->pending.begin() +
                            static_cast<std::deque<float>::difference_type>(start_sample),
                    rec->pending.begin() +
                            static_cast<std::deque<float>::difference_type>(end_sample));
        }
        if (gated && buf.size() < static_cast<size_t>(MIN_TRANSCRIBE_WINDOW_MS) *
                WHISPER_SAMPLE_RATE / 1000) {
            buf.clear();
        }
    }

    rec->pending.clear();
    // processed_audio_sec is credited inside transcribe_utterance, not here
    // — it counts only audio actually decoded, matching what
    // commit_compute_ms is the denominator for.
    advance_front(rec, n_pending);
    // The session is ending: nothing here continues into a next utterance,
    // so clear the watermarks outright rather than rely on advance_front's
    // general-case inference. This is redundant with reset() (which always
    // follows immediately today) but is one line of insurance against that
    // coupling changing later.
    rec->speech_until_ms = 0;
    rec->speech_start_ms = -1;
    rec->probed_until_ms = 0;
    const int max_utterance_ms = rec->max_utterance_ms;
    lock.unlock();

    if (!buf.empty()) {
        const size_t max_samples =
                static_cast<size_t>(max_utterance_ms) * WHISPER_SAMPLE_RATE / 1000;
        size_t offset = 0;
        while (offset < buf.size()) {
            const size_t take = std::min(max_samples, buf.size() - offset);
            transcribe_utterance(
                    rec, env, buf.data() + offset, take,
                    win_start_ms + static_cast<int64_t>(offset) * 1000 / WHISPER_SAMPLE_RATE,
                    /*is_flush=*/true);
            offset += take;
        }
    }

    return static_cast<jlong>(n_pending);
}


// Expose last-pass snapshot + session counters (including RTF) as a single
// struct so the Kotlin side can log it or later surface it in the UI.
// Acquires the same mutex as process()/flush() to get a consistent view.
JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_whisper_Whisper_getMetrics(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto rec = findRecognizer(handle);
    if (rec == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(rec->mtx);
    jclass cls =
            env->FindClass("app/versta/translate/core/entity/SpeechRecognitionMetrics");
    if (!cls) {
        return nullptr;
    }
    jmethodID cid = env->GetMethodID(
            cls, "<init>",
            "(JJJDDDJJJIIJIZFFFIII)V");
    if (!cid) {
        return nullptr;
    }
    double rtf = rec->commit_compute_ms > 0.0
            ? rec->processed_audio_sec * 1000.0 / rec->commit_compute_ms
            : 0.0;
    jobject obj = env->NewObject(cls, cid,
            static_cast<jlong>(rec->pass_count),
            static_cast<jlong>(rec->abort_count),
            static_cast<jlong>(rec->vad_skip_count),
            rec->processed_audio_sec,
            rec->commit_compute_ms,
            rtf,
            static_cast<jlong>(rec->last_pass_elapsed_ms),
            static_cast<jlong>(rec->last_pass_window_ms),
            static_cast<jlong>(rec->last_pass_n_samples),
            static_cast<jint>(rec->last_pass_audio_ctx),
            static_cast<jint>(rec->last_pass_max_tokens),
            static_cast<jlong>(rec->last_pass_budget_ms),
            static_cast<jint>(rec->last_pass_ret),
            rec->last_pass_was_flush ? JNI_TRUE : JNI_FALSE,
            rec->last_pass_encode_ms,
            rec->last_pass_decode_ms,
            rec->last_pass_batchd_ms,
            static_cast<jint>(rec->last_pass_n_encode),
            static_cast<jint>(rec->last_pass_n_decode),
            static_cast<jint>(rec->last_pass_n_batchd));
    return obj;
}


}  // extern "C"
