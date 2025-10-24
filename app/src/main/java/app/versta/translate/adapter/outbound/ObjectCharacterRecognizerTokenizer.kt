package app.versta.translate.adapter.outbound

import java.nio.file.Path

interface ObjectCharacterRecognizerTokenizer {
    val vocabSize: Long

    fun decode(ids: LongArray): String

    fun load(file: Path)
}
