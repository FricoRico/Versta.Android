package app.versta.translate.bridge.tokenize

import timber.log.Timber

class SentencePiece : AutoCloseable {
    private var handle = 0L

    init {
        handle = constructor()

        if (handle == 0L) {
            Timber.tag(TAG).e("Failed to create SentencePieceProcessor")
            throw IllegalStateException("Failed to create SentencePieceProcessor")
        }
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("SentencePiece is already closed")
            return
        }

        close(handle)
        handle = 0L
    }

    fun load(filename: String) {
        load(handle, filename)
    }

    fun loadFromSerializedProto(serialized: ByteArray) {
        loadFromSerializedProto(handle, serialized)
    }

    fun encodeAsPieces(input: String): List<String> {
        val pieces = encodeAsPieces(handle, input)
        return pieces.toList()
    }

    private external fun constructor(): Long
    private external fun close(handle: Long)
    private external fun load(handle: Long, filename: String)
    private external fun loadFromSerializedProto(handle: Long, serialized: ByteArray)
    private external fun encodeAsPieces(handle: Long, input: String): Array<String>

    companion object {
        private val TAG: String = SentencePiece::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
