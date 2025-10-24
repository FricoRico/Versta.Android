package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.tokenize.Vocabulary
import java.nio.file.Path
import kotlin.io.path.pathString

class PaddleTokenizer : ObjectCharacterRecognizerTokenizer {
    private var _vocabularyPath: String? = null

    private var _vocabulary: List<String> = emptyList()

    private val unknownToken = " "

    override val vocabSize: Long
        get() = _vocabulary.size.toLong()

    override fun decode(ids: LongArray): String {
        try {
            var tokens = ids.map { convertIdToToken(it)  }


            return tokens.joinToString("").trim()
        } catch (e: Exception) {
            throw IllegalArgumentException("Decoding ids: $ids", e)
        }
    }

    private fun convertIdToToken(id: Long): String {
        if (id < 1 || id >= vocabSize) {
            return unknownToken
        }

        return _vocabulary[id.toInt() - 1]
    }

    override fun load(
        file: Path
    ) {
        if (_vocabularyPath == file.pathString) {
            return
        }

        _vocabulary = Vocabulary.load(file.pathString)
        _vocabularyPath = file.pathString
    }

    companion object {

    }
}