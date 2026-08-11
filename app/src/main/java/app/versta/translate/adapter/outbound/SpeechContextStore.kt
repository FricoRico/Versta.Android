package app.versta.translate.adapter.outbound

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-language decoder context carry with a TTL, so a conversation (dictate,
 * swap languages to let the other side reply, swap back) can resume its
 * context instead of starting cold every session.
 *
 * This lives in Kotlin rather than the native
 * [app.versta.translate.bridge.whisper.Whisper] because a language
 * swap tears the recognizer down and rebuilds it — native state dies at
 * exactly the turn boundary this store exists to bridge. Keying by language
 * also guarantees a carry can never leak across languages (priming one
 * language's decode with another language's text causes code-switching),
 * which a single native slot could not.
 *
 * [record] is fed the `contextTokenIds` from
 * [app.versta.translate.bridge.whisper.WhisperSegmentCallback.onSegment];
 * [get] is read once per session start (right after loading the recognizer
 * for that language) and passed to
 * [app.versta.translate.bridge.whisper.Whisper.setCarriedContext].
 * Within a session, the native side chains context from utterance to
 * utterance on its own — this store only needs to bridge across session
 * boundaries.
 */
class SpeechContextStore(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val tokens: IntArray, val capturedAtMs: Long)

    // record() runs on the process thread (segment callback) while get() runs
    // on whoever calls start(); a session teardown can also be flushing its
    // last segment concurrently with a new session's start().
    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Records [tokens] as the carried context for [languageIsoCode]. A
     * null/blank language or a null/empty token array is a no-op — an empty
     * array from
     * [app.versta.translate.bridge.whisper.WhisperSegmentCallback.onSegment]
     * means that utterance's decode did not pass the native quality gate,
     * and any existing standing context for this language (if still within
     * TTL) should be left untouched rather than cleared by a single bad
     * utterance.
     */
    fun record(languageIsoCode: String?, tokens: IntArray?) {
        if (languageIsoCode.isNullOrBlank() || tokens == null || tokens.isEmpty()) {
            return
        }
        entries[languageIsoCode] = Entry(tokens, now())
    }

    /**
     * Returns the still-valid carried context for [languageIsoCode], or an
     * empty array if none exists or it has aged past [ttlMs]. Non-mutating
     * beyond lazily evicting an expired entry — the TTL is anchored to when
     * this language was last actually [record]ed, not to how often [get] is
     * called, so repeatedly checking (e.g. swapping back and forth) does not
     * itself keep a stale context alive.
     */
    fun get(languageIsoCode: String?): IntArray {
        if (languageIsoCode.isNullOrBlank()) {
            return EMPTY
        }
        val entry = entries[languageIsoCode] ?: return EMPTY
        if (now() - entry.capturedAtMs > ttlMs) {
            entries.remove(languageIsoCode)
            return EMPTY
        }
        return entry.tokens
    }

    /** Clears all carried context for every language, e.g. on hard teardown. */
    fun clear() {
        entries.clear()
    }

    companion object {
        // ~ how long a reply on the other side of a conversation is expected
        // to take; longer than that and the topic has likely moved on, so a
        // stale carry is more likely to hurt than help.
        const val DEFAULT_TTL_MS: Long = 90_000
        private val EMPTY = IntArray(0)
    }
}
