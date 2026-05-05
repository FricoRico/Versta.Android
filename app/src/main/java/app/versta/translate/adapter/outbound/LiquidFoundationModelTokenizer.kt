package app.versta.translate.adapter.outbound

import timber.log.Timber
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiquidFoundationModelTokenizer {
    companion object {
        private const val TAG = "LiquidFoundationModelTokenizer"
        private const val MAGIC = 0x324D464C // "LFM2" in Little Endian

        private fun buildUnicodeToByteMap(): Map<Int, Int> {
            val map = mutableMapOf<Int, Int>()
            val bs = mutableListOf<Int>()
            val cs = mutableListOf<Int>()

            for (b in 0x21..0x7E) { bs.add(b); cs.add(b) }
            for (b in 0xA1..0xAC) { bs.add(b); cs.add(b) }
            for (b in 0xAE..0xFF) { bs.add(b); cs.add(b) }

            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(0x100 + n)
                    n++
                }
            }
            
            for (i in bs.indices) {
                map[cs[i]] = bs[i]
            }
            return map
        }

        private val UNICODE_TO_BYTE = buildUnicodeToByteMap()
    }

    private var _vocabSize: Int = 0
    private val _specialTokens = mutableMapOf<Int, String>()
    private val _specialTokenIds = mutableMapOf<String, Int>()
    private var _vocab: Array<String> = emptyArray()
    private var _merges: Array<IntArray> = emptyArray()
    private val _mergeMap = mutableMapOf<String, Int>()
    private val _byteToId = IntArray(256) { -1 }

    val vocabSize: Int
        get() = _vocabSize

    val bosId: Int
        get() = _specialTokenIds["<|startoftext|>"] ?: 1

    val eosId: Int
        get() = _specialTokenIds["</s|"] ?: 7

    val padId: Int
        get() = _specialTokenIds["<|padding|>"] ?: 0

    fun load(stream: InputStream) {
        val data = stream.readBytes()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        if (buffer.remaining() < 20) throw IllegalStateException("Buffer too small for header")

        // 1. Verify Magic
        val magic = buffer.int
        if (magic != MAGIC) {
            throw IllegalStateException("Invalid magic number: expected ${Integer.toHexString(MAGIC)}, found ${Integer.toHexString(magic)}")
        }

        // 2. Read Counts
        val vCount = buffer.int
        val mCount = buffer.int
        val sCount = buffer.int
        val bCount = buffer.int

        // 3. Load Vocab
        val vocabArray = arrayOfNulls<String>(vCount)
        for (i in 0 until vCount) {
            if (buffer.remaining() < 1) throw IllegalStateException("Buffer underflow in vocab load")
            val startIdx = buffer.position()
            var nullIdx = -1
            for (j in 0 until (data.size - startIdx)) {
                if (data[startIdx + j].toInt() == 0) {
                    nullIdx = j
                    break
                }
            }
            if (nullIdx != -1) {
                val end = startIdx + nullIdx
                val token = String(data, startIdx, end - startIdx, Charsets.UTF_8)
                vocabArray[i] = token
                buffer.position(end + 1) 
                if (buffer.remaining() >= 4) buffer.int // Skip length field
            } else {
                throw IllegalStateException("Null terminator not found in vocab at index $i")
            }
        }
        @Suppress("UNCHECKED_CAST")
        _vocab = (vocabArray as Array<String>)
        _vocabSize = _vocab.size

        // 4. Load Merges
        val mergesArray = Array(mCount) { intArrayOf(0, 0, 0) }
        for (i in 0 until mCount) {
            if (buffer.remaining() < 12) throw IllegalStateException("Unexpected end of buffer while loading merges")
            val left = buffer.int
            val right = buffer.int
            val merged = buffer.int
            mergesArray[i] = intArrayOf(left, right, merged)
        }
        _merges = mergesArray

        // 5. Load Special Tokens
        for (i in 0 until sCount) {
            if (buffer.remaining() < 4) throw IllegalStateException("Unexpected end of buffer while loading special tokens")
            val id = buffer.int
            val startIdx = buffer.position()
            var nullIdx = -1
            for (j in 0 until (data.size - startIdx)) {
                if (data[startIdx + j].toInt() == 0) {
                    nullIdx = j
                    break
                }
            }
            if (nullIdx != -1) {
                val end = startIdx + nullIdx
                val token = String(data, startIdx, end - startIdx, Charsets.UTF_8)
                buffer.position(end + 1) // Skip null terminator
                if (buffer.remaining() >= 8) {
                    buffer.int // skip length
                    val isSpecial = buffer.int != 0
                    _specialTokens[id] = token
                    _specialTokenIds[token] = id
                }
            }
        }

        // 6. Load Byte-to-ID
        for (i in 0 until 256) {
            if (buffer.remaining() < 4) break
            _byteToId[i] = buffer.int
        }

        buildMergeMap()
        Timber.d("Tokenizer loaded successfully: %d tokens, %d merges, %d special tokens", _vocabSize, mCount, sCount)
    }

    fun encode(text: String): LongArray {
        val tokens = tokenize(text)
        return tokens.map { it.toLong() }.toLongArray()
    }

    fun decode(ids: LongArray, filterSpecialTokens: Boolean = true): String {
        val tokens = ids.map { id ->
            val idInt = id.toInt()
            if (idInt < 0 || idInt >= _vocabSize) {
                _specialTokens[idInt] ?: ""
            } else {
                _vocab[idInt]
            }
        }

        var result = tokens.joinToString("")

        if (filterSpecialTokens) {
            _specialTokenIds.keys.forEach { token ->
                result = result.replace(token, "")
            }
        }

        return byteLevelDecode(result)
    }

    private fun byteLevelDecode(text: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < text.length) {
            val charCode = text.codePointAt(i)
            val byte = UNICODE_TO_BYTE[charCode]
            if (byte != null) {
                bytes.add(byte.toByte())
            } else {
                val encoded = text.substring(i, i + Character.charCount(charCode)).toByteArray(Charsets.UTF_8)
                bytes.addAll(encoded.toList())
            }
            i += Character.charCount(charCode)
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun tokenize(text: String): List<Int> {
        val words = splitText(text)
        val allTokens = mutableListOf<Int>()
        for (word in words) {
            val tokens = byteLevelEncode(word)
            allTokens.addAll(tokens)
        }
        return listOf(bosId) + applyBPE(allTokens)
    }

    private fun splitText(text: String): List<String> {
        val regex = Regex("""(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}{1,3}| ?[^\s\p{L}\p{N}]+[\r\n]*|\s*[\r\n]+|\s+(?!\S)|\s+""")
        return regex.findAll(text).map { it.value }.filter { it.isNotEmpty() }.toList()
    }

    private fun byteLevelEncode(text: String): List<Int> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val tokens = mutableListOf<Int>()
        for (b in bytes) {
            val id = _byteToId[b.toInt() and 0xFF]
            if (id >= 0) {
                tokens.add(id)
            } else {
                Timber.w("Unmapped byte 0x%02X in text", b.toInt() and 0xFF)
            }
        }
        return tokens
    }

    private fun applyBPE(tokens: List<Int>): List<Int> {
        if (tokens.isEmpty()) return tokens
        val tokenList = tokens.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            for (merge in _merges) {
                val leftId = merge[0]
                val rightId = merge[1]
                val mergedId = merge[2]
                var i = 0
                while (i < tokenList.size - 1) {
                    if (tokenList[i] == leftId && tokenList[i + 1] == rightId) {
                        tokenList[i] = mergedId
                        tokenList.removeAt(i + 1)
                        changed = true
                    } else {
                        i++
                    }
                }
            }
        }
        return tokenList
    }

    private fun buildMergeMap() {
        _mergeMap.clear()
        for (merge in _merges) {
            val key = "${merge[0]} ${merge[1]}"
            _mergeMap[key] = merge[2]
        }
    }
}
