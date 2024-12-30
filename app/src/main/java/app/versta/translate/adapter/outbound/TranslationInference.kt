package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelInferenceFiles
import kotlinx.coroutines.flow.Flow

interface TranslationInference {
    fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beams: Int = 6,
        maxSequenceLength: Int = 128
    ): LongArray

    fun runAsFlow(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beams: Int = 6,
        maxSequenceLength: Int = 128
    ): Flow<LongArray>

    fun load(files: LanguageModelInferenceFiles)
}