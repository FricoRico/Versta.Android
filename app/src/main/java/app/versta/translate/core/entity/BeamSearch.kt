package app.versta.translate.core.entity

import android.util.Log
import androidx.compose.ui.util.fastDistinctBy
import app.versta.translate.bridge.inference.BeamSearch
import kotlinx.coroutines.asCoroutineDispatcher
import java.nio.FloatBuffer
import java.util.concurrent.Executors
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
    beamSize: Int,
    private val minP: Float,
    private val padId: Long,
    private val eosId: Long,
) {
    private var _beams: List<Beam> = List(beamSize) {
        Beam(it, longArrayOf(padId), -1e-9f)
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

    fun search(logits: DecoderLogits, size: Int): IntArray {
        val beams = mutableListOf<Beam>()

        for (i in _beams.indices) {
            val probabilities = BeamSearch.softmax(logits[i].last())
            val indices = BeamSearch.minPIndices(probabilities, minP)

            for (token in indices) {
                val sequence = _beams[i].sequence + token.toLong()
                val logit = probabilities[token].coerceAtLeast(-1e-9f)
                val score = _beams[i].score + ln(logit)

                beams.add(Beam(i, sequence, score))
            }
        }

        _beams = beams.fastDistinctBy { it.hashCode() }.sortedByDescending { it.score }.take(size)
        return _beams.map { it.id }.toIntArray()
    }
}
