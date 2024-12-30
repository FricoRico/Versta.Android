package app.versta.translate.core.entity

data class DecoderMetadata(
    val batchSize: Int,
    val sequenceLength: Int,
) {
    private val completedBatches: BooleanArray = BooleanArray(batchSize) { false }
    private var completedBatchCount: Int = 0

    fun isBatchComplete(index: Int): Boolean {
        return completedBatches[index]
    }

    fun isDoneDecoding(): Boolean {
        return completedBatchCount == batchSize
    }

    fun markBatchComplete(index: Int) {
        completedBatches[index] = true
        completedBatchCount++
    }
}