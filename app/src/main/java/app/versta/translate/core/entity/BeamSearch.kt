package app.versta.translate.core.entity

import app.versta.translate.bridge.inference.BeamSearch
import kotlin.math.ln

data class Beam(val sequence: LongArray, val score: Float) {
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
    private val eosId: Long
) {
    private var _beams: List<Beam> = List(size) {
        Beam(longArrayOf(padId), 1f)
    }

    fun last(): LongArray {
        return _beams.map { it.sequence.last() }.toLongArray()
    }

    fun lastTokenInBeam(beamIndex: Int): Long {
        return _beams[beamIndex].sequence.last()
    }

    fun complete(): Boolean {
        return _beams.all { it.sequence.lastOrNull() == eosId }
    }

    fun best(): LongArray {
        return _beams.maxByOrNull { it.score }?.sequence ?: longArrayOf()
    }

    fun search(logits: DecoderLogits, beamsSize: Int) {
        var newBeams = arrayOf<Beam>()
        for (i in _beams.indices) {
            // Retrieve top-k tokens for each beam independently
            val topKIndices = BeamSearch.topKIndices(logits[i][0], 8)

            // Expand each beam with top-k tokens
            for (token in topKIndices) {
                val newSeq = _beams[i].sequence + token.toLong()
                val logitValue = logits[i][0][token].coerceAtLeast(1e-9f)
                val newScore = _beams[i].score + ln(logitValue)

                newBeams = newBeams.plus(Beam(newSeq, newScore))
            }
        }

        _beams = newBeams.sortedByDescending { it.score }.take(beamsSize).toMutableList()
    }
}
