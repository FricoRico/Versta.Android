package app.versta.translate.bridge.tokenize

import app.versta.translate.bridge.tokenize.SentencePiece.constructor
import app.versta.translate.bridge.tokenize.SentencePiece.destructor
import app.versta.translate.bridge.tokenize.SentencePiece.encodeAsPieces
import app.versta.translate.bridge.tokenize.SentencePiece.load
import app.versta.translate.bridge.tokenize.SentencePiece.loadFromSerializedProto

internal object SentencePiece {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun constructor(): Long
    external fun destructor(spp: Long)
    external fun load(spp: Long, filename: String)
    external fun loadFromSerializedProto(spp: Long, serialized: ByteArray)
    external fun encodeAsPieces(spp: Long, input: String): Array<String>
}

class SentencePieceProcessor : AutoCloseable {
    private var instance: Long = 0

    init {
        instance = constructor()
    }

    override fun close() {
        destructor(instance)
    }

    fun load(filename: String) {
        load(instance, filename)
    }

    fun loadFromSerializedProto(serialized: ByteArray) {
        loadFromSerializedProto(instance, serialized)
    }

    fun encodeAsPieces(input: String): List<String> {
        val pieces = encodeAsPieces(instance, input)
        return pieces.toList()
    }
}
