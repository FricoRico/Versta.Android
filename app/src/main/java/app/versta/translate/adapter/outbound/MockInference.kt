package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelInferenceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockInference: TranslationInference {
    override fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): LongArray {
        return LongArray(0)
    }

    override fun runAsFlow(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<LongArray> {
        return flowOf(LongArray(0))
    }

    override fun load(files: LanguageModelInferenceFiles, threads: Int) {
        return
    }

    override fun close() {
        return
    }
}