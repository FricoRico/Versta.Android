package app.versta.translate.core.model

import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.core.entity.PivotPairModelFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates the offline translation pipeline: input sanitization, pivot
 * chaining across an optional intermediary model, model loading, and engine
 * recreation when the cache configuration changes.
 *
 * Pure Kotlin: it depends only on the [TranslationInference] port and the
 * translation preference flows, so it is constructible without Android
 * framework classes and unit-testable without a device. UI state plumbing
 * (`StateFlow`s) stays in [TranslationViewModel].
 *
 * There is deliberately no cancel API: translations are sub-200 ms, so a
 * cancel affordance is unreachable in time (ADR-0001, R1.7).
 */
class TranslationOrchestrator(
    intermediateModel: TranslationInference,
    outputModel: TranslationInference,
    private val translationPreferenceRepository: TranslationPreferenceRepository,
    private val createInference: (cacheSize: Long) -> TranslationInference,
) {
    private var intermediateModel: TranslationInference = intermediateModel
    private var outputModel: TranslationInference = outputModel

    private val loadMutex = Mutex()

    /**
     * Translates [input] into the output language. When [files] carries an
     * intermediary model, the input is chained through it first (pivot
     * translation).
     *
     * @return the translated text.
     * @throws Exception when the underlying engine fails; error surfacing is
     * the caller's concern.
     */
    suspend fun translate(
        input: String,
        files: PivotPairModelFiles?,
    ): String {
        val sanitized = sanitize(input)

        val intermediate = loadMutex.withLock { intermediateModel }
        val output = loadMutex.withLock { outputModel }

        var text = sanitized

        if (files?.intermediary != null) {
            text =
                intermediate.translate(
                    text = text,
                    maxBeamWidth = translationPreferenceRepository.getNumberOfBeams().first(),
                    maxSequenceLength = translationPreferenceRepository.getMaxSequenceLength().first(),
                )
        }

        return output.translate(
            text = text,
            maxBeamWidth = translationPreferenceRepository.getNumberOfBeams().first(),
            maxSequenceLength = translationPreferenceRepository.getMaxSequenceLength().first(),
        )
    }

    /**
     * Loads [files] into the engines: the intermediary model when configured
     * and the output model when present. Without an intermediary model the
     * intermediate engine is closed so a stale model is never chained.
     */
    suspend fun load(files: PivotPairModelFiles?) {
        loadMutex.withLock {
            if (files?.intermediary != null) {
                intermediateModel.load(files.intermediary.files, files.intermediary.config)
            } else {
                intermediateModel.close()
            }

            if (files?.output != null) {
                outputModel.load(files.output.files, files.output.config)
            }
        }
    }

    /**
     * Closes both engines, releasing any loaded model state.
     */
    suspend fun unload() {
        loadMutex.withLock {
            intermediateModel.close()
            outputModel.close()
        }
    }

    /**
     * Recreates both engines with the effective cache size (see
     * [effectiveCacheSize]). leanmt fixes the cache size at engine
     * construction, so applying a new size requires fresh engines. The caller
     * reloads the currently selected model afterwards.
     */
    suspend fun recreateEngines() {
        val cacheSize = effectiveCacheSize()

        loadMutex.withLock {
            intermediateModel.close()
            outputModel.close()

            intermediateModel = createInference(cacheSize)
            outputModel = createInference(cacheSize)
        }
    }

    /**
     * Gets the effective cache size: the configured size while the cache is
     * enabled, or the [CACHE_SIZE_DISABLED] sentinel while disabled. The
     * configured size is preserved untouched so it is restored on re-enable.
     */
    private suspend fun effectiveCacheSize(): Long =
        if (translationPreferenceRepository.getCacheEnabled().first()) {
            translationPreferenceRepository.getCacheSize().first().toLong()
        } else {
            CACHE_SIZE_DISABLED
        }

    /**
     * Sanitizes the input string by removing any undefined characters.
     */
    private fun sanitize(input: String): String {
        val filteredString = input.filter { it.isDefined() }
        val utf8Bytes = filteredString.toByteArray(Charsets.UTF_8)

        return String(utf8Bytes, Charsets.UTF_8)
    }

    companion object {
        private const val CACHE_SIZE_DISABLED: Long = -2L
    }
}
