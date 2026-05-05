package app.versta.translate.adapter.outbound

import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LiquidFoundationModelTokenizer
import org.junit.Test
import timber.log.Timber

class LiquidFoundationModelTokenizerTest {

    @Test
    fun testEncodeHelloWorld() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = LiquidFoundationModelTokenizer()

        tokenizer.load(
            stream = context.resources.openRawResource(R.raw.tokenizer),
        )

        val input = "Hello world"
        val ids = tokenizer.encode(input)

        Timber.d("=== LiquidFoundationModelTokenizer Test ===")
        Timber.d("Input: %s", input)
        Timber.d("Vocab size: %d", tokenizer.vocabSize)
        Timber.d("BOS ID: %d", tokenizer.bosId)
        Timber.d("EOS ID: %d", tokenizer.eosId)
        Timber.d("PAD ID: %d", tokenizer.padId)
        Timber.d("Token IDs: %s", ids.contentToString())
        Timber.d("Token count: %d", ids.size)

        val tokenStrings = ids.map { id ->
            tokenizer.decode(longArrayOf(id), filterSpecialTokens = false)
        }

        Timber.d("Token strings: %s", tokenStrings.joinToString(" | "))

        // Decode back to text
        val decoded = tokenizer.decode(ids)
        Timber.d("Round-trip decoded: %s", decoded)
    }

    @Test
    fun testRoundTripDecode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = LiquidFoundationModelTokenizer()

        tokenizer.load(
            stream = context.resources.openRawResource(R.raw.tokenizer),
        )

        val input = "Hello world"
        val ids = tokenizer.encode(input)
        val decoded = tokenizer.decode(ids)

        Timber.d("=== Round Trip Test ===")
        Timber.d("Original: %s", input)
        Timber.d("Encoded IDs: %s", ids.contentToString())
        Timber.d("Decoded: %s", decoded)
    }

    @Test
    fun testEncodeWithSpecialTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = LiquidFoundationModelTokenizer()

        tokenizer.load(
            stream = context.resources.openRawResource(R.raw.tokenizer),
        )

        val input = "Hello world"
        val ids = tokenizer.encode(input)

        Timber.d("=== Special Token Check ===")
        Timber.d("BOS token present: %s", ids.first() == tokenizer.bosId.toLong())
        Timber.d("First ID: %d (expected BOS: %d)", ids.first(), tokenizer.bosId)

        // Show tokens without BOS
        val contentTokens = ids.drop(1).toTypedArray()
        Timber.d("Content token IDs: %s", contentTokens.contentToString())
    }
}
