package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelInferenceFiles
import kotlinx.coroutines.flow.Flow

interface TranslationInference {
    fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): LongArray

    fun runAsFlow(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<LongArray>

    fun load(files: LanguageModelInferenceFiles, threads: Int)

    fun close()
}