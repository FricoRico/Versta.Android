package app.versta.translate.core.entity

import androidx.compose.ui.util.fastDistinctBy
import app.versta.translate.bridge.inference.BeamSearch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ln

data class Beam(val id: Int, val sequence: LongArray, val score: Float) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Beam

        if (!sequence.contentEquals(other.sequence)) return false
        if (score != other.score) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequence.contentHashCode()
        result = 31 * result + score.hashCode()
        return result
    }
}

class BeamSearch(
    size: Int,
    private val padId: Long,
    private val eosId: Long,
) {
    private var _beams: List<Beam> = List(size) {
        Beam(it, longArrayOf(padId), -1e-9f)
    }

    fun ids(): IntArray {
        return _beams.map { it.id }.toIntArray()
    }

    fun lastTokens(): Array<LongArray> {
        return _beams.map { longArrayOf(it.sequence.last()) }.toTypedArray()
    }

    fun complete(): Boolean {
        return _beams.all { it.sequence.lastOrNull() == eosId }
    }

    fun best(): LongArray {
        return _beams.maxByOrNull { it.score }?.sequence ?: longArrayOf()
    }

    fun search(logits: DecoderLogits, size: Int) {
        val beams = mutableListOf<Beam>()

        for (i in _beams.indices) {
            val probabilities = BeamSearch.softmax(logits[i].last())
            val indices = BeamSearch.minPIndices(probabilities, 1e-4f)

            for (token in indices) {
                val sequence = _beams[i].sequence + token.toLong()
                val logit = probabilities[token].coerceAtLeast(-1e-9f)
                val score = _beams[i].score + ln(logit)

                beams.add(Beam(i, sequence, score))
            }
        }

        _beams = beams.fastDistinctBy { it.hashCode() }.sortedByDescending { it.score }.take(size)
    }

    fun transposeCache(shape: LongArray, buffer: FloatBuffer, count: Int): FloatBuffer {
        val ids = _beams.map { it.id }.toIntArray()

        val batches = shape.first().toInt()
        val chunks = (count / batches)

        val cached = FloatArray(count)
        buffer.rewind()
        buffer.get(cached)

        val transposed = FloatArray(count)

        for (index in ids.indices) {
            val target = ids[index]

            val source = target * chunks
            val destination = index * chunks

            System.arraycopy(cached, source, transposed, destination, chunks)
        }

        val byteBuffer = ByteBuffer
            .allocateDirect(count * 4)
            .order(ByteOrder.nativeOrder())

        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(transposed)
        floatBuffer.rewind()

        return floatBuffer
    }
}
