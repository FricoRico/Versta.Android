package app.versta.translate.adapter.outbound


import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.core.entity.KokoroInput
import app.versta.translate.core.entity.KokoroOutput
import app.versta.translate.core.entity.TextToSpeechInferenceFiles
import app.versta.translate.core.entity.Waveform
import org.jetbrains.bio.npy.NpyFile
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.math.max
import kotlin.math.min

internal const val MIN_SPEED = 0.7f
internal const val MAX_SPEED = 1.4f
const val MAX_TOKEN_LENGTH = 509
const val STYLE_DIM = 256

class KokoroInference(private val ortEnvironment: OrtEnvironment): TextToSpeechInference {
    private var kokoroSession: OrtSession? = null
    private var kokoroVoice: FloatArray? = null

    override fun synthesize(tokens: LongArray, speed: Float): Waveform {
        if (kokoroSession == null) {
            throw IllegalStateException("Session is not loaded")
        }

        val kokoroInput = KokoroInput(
            ortEnvironment = ortEnvironment,
            tokens = tokens,
            style = getStyle(tokens),
            speed = getSpeed(speed, tokens.size)
        )

        val kokoroOutput = KokoroOutput()

        try {
            val inputs = kokoroInput.get()
            val output = kokoroOutput.parse(kokoroSession!!.run(inputs))

            return output ?: throw IllegalStateException("Waveform output is null")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            throw e
        } finally {
            kokoroInput.destroy()
            kokoroOutput.destroy()
        }
    }

    override fun setVoice(file: Path?) {
        if (file == null) {
            kokoroVoice = null
            return
        }

        kokoroVoice = NpyFile.read(file).asFloatArray()
    }

    private fun getStyle(tokens: LongArray): FloatArray {
        if (kokoroVoice == null) {
            throw IllegalStateException("Voice style is not loaded")
        }

        val length = max(0, min(MAX_TOKEN_LENGTH, tokens.size))
        val styleArray = FloatArray(STYLE_DIM)

        for (i in 0 until STYLE_DIM) {
            styleArray[i] = kokoroVoice!![length * STYLE_DIM + i]
        }

        return styleArray
    }

    private fun getSpeed(speed: Float, tokens: Int): Float {
        val minTokens = 1
        val clamped = minTokens.coerceAtLeast(tokens.coerceAtMost(MAX_TOKEN_LENGTH))

        return speed * (MAX_SPEED - (clamped - minTokens) * (MAX_SPEED - MIN_SPEED) / (MAX_TOKEN_LENGTH - minTokens))
    }

    override fun load(files: TextToSpeechInferenceFiles, threads: Int) {
        close()

        val modelFile = File(files.model.pathString)
        val options = OrtSession.SessionOptions().apply {
            addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            addConfigEntry("kOrtSessionOptionsConfigAllowIntraOpSpinning", "0")
        }

        kokoroSession = ortEnvironment.createSession(readFileToByteBuffer(modelFile), options)
    }

    fun close() {
        kokoroSession?.close()
    }


    private fun readFileToByteBuffer(file: File): ByteBuffer {
        FileInputStream(file).use { inputStream ->
            val channel = inputStream.channel
            val size = channel.size()
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
            return buffer
        }
    }

    companion object {
        private val TAG = KokoroInference::class.java.simpleName
    }
}