package app.versta.translate.adapter.outbound

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.DecoderInput
import app.versta.translate.core.entity.DecoderOutput
import app.versta.translate.core.entity.EncoderAttentionMasks
import app.versta.translate.core.entity.EncoderHiddenStates
import app.versta.translate.core.entity.EncoderInput
import app.versta.translate.core.entity.EncoderOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import kotlin.io.path.pathString

class MarianInference(
    threadCount: Int = 4,
) : TranslationInference {
    private val ortEnvironment =
        OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL,
            "OpusInference",
            OrtEnvironment.ThreadingOptions().apply {
                setGlobalSpinControl(false)
            })

    private val sessionOptions = OrtSession.SessionOptions().apply {
        setCPUArenaAllocator(true)
        setMemoryPatternOptimization(true)
        setInterOpNumThreads(threadCount)
        addXnnpack(mapOf("intra_op_num_threads" to threadCount.toString()))
        registerCustomOpLibrary(OrtxPackage.getLibraryPath())
    }

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private fun encode(
        inputIds: LongArray, attentionMask: LongArray
    ): EncoderHiddenStates {
        if (encoderSession == null) {
            throw IllegalStateException("Encoder session is not loaded")
        }

        val encoderInput = EncoderInput(
            ortEnvironment = ortEnvironment,
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        val encoderOutput = EncoderOutput()

        try {
            val inputs = encoderInput.get()
            val output = encoderOutput.parse(encoderSession!!.run(inputs))

            return output ?: throw IllegalStateException("Encoder output is null")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            encoderInput.close()
        }
    }


    private fun decode(
        encoderHiddenStates: EncoderHiddenStates,
        attentionMask: EncoderAttentionMasks,
        eosId: Long,
        padId: Long,
        beamsSize: Int,
        maxSequenceLength: Int,
    ): LongArray {
        if (decoderSession == null) {
            throw IllegalStateException("Encoder session is not loaded")
        }

        val beams = app.versta.translate.core.entity.BeamSearch(
            size = beamsSize,
            padId = padId,
            eosId = eosId
        )

        val decoderInput = DecoderInput(
            ortEnvironment = ortEnvironment,
            encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
            encoderAttentionMask = Array(beamsSize) { attentionMask }
        )

        val decoderOutput = DecoderOutput()

        try {
            for (step in 0 until maxSequenceLength) {
                if (beams.complete()) {
                    break
                }

                val decoderInputIds = Array(beamsSize) { i ->
                    longArrayOf(beams.lastTokenInBeam(i))
                }

                val inputs = decoderInput.get(decoderInputIds, decoderOutput.cache)
                val output = decoderOutput.parse(decoderSession!!.run(inputs)) ?: break

                beams.search(
                    logits = output,
                    beamsSize = beamsSize
                )
            }

            return beams.best()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            decoderInput.close()
        }
    }

    private fun decodeAsFlow(
        encoderHiddenStates: EncoderHiddenStates,
        attentionMask: EncoderAttentionMasks,
        eosId: Long,
        padId: Long,
        beamsSize: Int,
        maxSequenceLength: Int,
    ): Flow<LongArray> {
        if (decoderSession == null) {
            throw IllegalStateException("Encoder session is not loaded")
        }

        return flow {
            val beams = app.versta.translate.core.entity.BeamSearch(
                size = beamsSize,
                padId = padId,
                eosId = eosId
            )

            val decoderInput = DecoderInput(
                ortEnvironment = ortEnvironment,
                encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
                encoderAttentionMask = Array(beamsSize) { attentionMask }
            )

            val decoderOutput = DecoderOutput()

            try {

                for (step in 0 until maxSequenceLength) {
                    if (beams.complete()) {
                        break
                    }

                    val decoderInputIds = Array(beamsSize) { i ->
                        longArrayOf(beams.lastTokenInBeam(i))
                    }

                    val inputs = decoderInput.get(
                        inputIds = decoderInputIds,
                        cache = decoderOutput.cache
                    )
                    val output =
                        decoderOutput.parse(decoderSession!!.run(inputs)) ?: break

                    beams.search(
                        logits = output,
                        beamsSize = beamsSize
                    )

                    emit(beams.best())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            } finally {
                decoderInput.close()
            }
        }
    }

    override fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beams: Int,
        maxSequenceLength: Int
    ): LongArray {
        val encoderHiddenStates = encode(
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        return decode(
            encoderHiddenStates = encoderHiddenStates,
            attentionMask = attentionMask,
            eosId = eosId,
            padId = padId,
            beamsSize = beams,
            maxSequenceLength = maxSequenceLength
        )
    }

    override fun runAsFlow(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beams: Int,
        maxSequenceLength: Int
    ): Flow<LongArray> {
        val encoderHiddenStates = encode(
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        return decodeAsFlow(
            encoderHiddenStates = encoderHiddenStates,
            attentionMask = attentionMask,
            eosId = eosId,
            padId = padId,
            beamsSize = beams,
            maxSequenceLength = maxSequenceLength
        )
    }

    override fun load(files: LanguageModelInferenceFiles) {
        close()

        val encoderFile = File(files.encoder.pathString).readBytes()
        val decoderFile = File(files.decoder.pathString).readBytes()

        encoderSession = ortEnvironment.createSession(encoderFile, sessionOptions)
        decoderSession = ortEnvironment.createSession(decoderFile, sessionOptions)
    }

    fun close() {
        encoderSession?.close()
        decoderSession?.close()

        ortEnvironment.close()
    }

    companion object {
        private val TAG: String = MarianInference::class.java.simpleName
    }
}
