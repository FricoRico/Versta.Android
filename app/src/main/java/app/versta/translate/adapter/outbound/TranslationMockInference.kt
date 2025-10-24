package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelInferenceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TranslationMockInference: TranslationInference {
    override fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
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
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<LongArray> {
        return flowOf(LongArray(0))
    }

    override fun runBatch(
        inputIds: Array<LongArray>,
        attentionMask: Array<LongArray>,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Array<LongArray> {
        return Array(inputIds.size) { LongArray(0) }
    }

    override fun runBatchAsFlow(
        inputIds: Array<LongArray>,
        attentionMask: Array<LongArray>,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<Array<LongArray>> {
        return flowOf(Array(inputIds.size) { LongArray(0) })
    }

    override fun cancel() {
        return
    }

    override fun load(files: LanguageModelInferenceFiles, threads: Int) {
        return
    }

    override fun close() {
        return
    }
}