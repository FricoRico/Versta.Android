package app.versta.translate.adapter.outbound

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import app.versta.translate.bridge.inference.BeamSearch
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.DecoderInput
import app.versta.translate.core.entity.DecoderOutput
import app.versta.translate.core.entity.EncoderAttentionMasks
import app.versta.translate.core.entity.EncoderHiddenStates
import app.versta.translate.core.entity.EncoderInput
import app.versta.translate.core.entity.EncoderOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import kotlin.io.path.pathString

class MarianInference : TranslationInference {

    private val ortEnvironment =
        OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL,
            "OpusInference",
            OrtEnvironment.ThreadingOptions().apply {
                setGlobalSpinControl(false)
            })

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private var runInference = false

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
            Timber.e(e)
            throw e
        } finally {
            encoderInput.destroy()
            encoderOutput.destroy()
        }
    }

    private fun decode(
        encoderHiddenStates: EncoderHiddenStates,
        attentionMask: EncoderAttentionMasks,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamsSize: Int,
        maxSequenceLength: Int,
        completeOnRepeat: Boolean
    ): LongArray {
        if (decoderSession == null) {
            throw IllegalStateException("Decoder session is not loaded")
        }

        val beamSearch = BeamSearch(
            beamSize = beamsSize,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            padId = padId,
            eosId = eosId
        )

        val decoderInput = DecoderInput(
            ortEnvironment = ortEnvironment,
            encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
            encoderAttentionMask = Array(beamsSize) { attentionMask }
        )

        val decoderOutput = DecoderOutput(
            ortEnvironment = ortEnvironment,
            beamSearch = beamSearch
        )

        var step = 0

        try {
            while (runInference && step < maxSequenceLength) {
                step++

                if (beamSearch.complete(completeOnRepeat)) {
                    break;
                }

                val inputs = decoderInput.get(
                    inputIds = beamSearch.lastTokens(),
                    cache = decoderOutput.cache
                )

                val outputs = decoderSession!!.run(inputs)
                decoderInput.close()

                decoderOutput.search(outputs)
                decoderOutput.cache(outputs)

                outputs.close()
            }

            val result = beamSearch.best().plus(eosId)

            if (completeOnRepeat) {
                return distinct(result)
            }

            return result
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        } finally {
            decoderInput.destroy()
            decoderOutput.destroy()
        }
    }

    private fun decodeAsFlow(
        encoderHiddenStates: EncoderHiddenStates,
        attentionMask: EncoderAttentionMasks,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamsSize: Int,
        maxSequenceLength: Int,
        completeOnRepeat: Boolean
    ): Flow<LongArray> {
        if (decoderSession == null) {
            throw IllegalStateException("Decoder session is not loaded")
        }

        return flow {
            val beamSearch = BeamSearch(
                beamSize = beamsSize,
                minP = minP,
                repetitionPenalty = repetitionPenalty,
                padId = padId,
                eosId = eosId,
            )

            val decoderInput = DecoderInput(
                ortEnvironment = ortEnvironment,
                encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
                encoderAttentionMask = Array(beamsSize) { attentionMask }
            )

            val decoderOutput = DecoderOutput(
                ortEnvironment = ortEnvironment,
                beamSearch = beamSearch
            )

            var step = 0

            try {
                while (runInference && step < maxSequenceLength) {
                    step++

                    if (beamSearch.complete(completeOnRepeat)) {
                        val result = beamSearch.best().plus(eosId)

                        if (completeOnRepeat) {
                            emit(distinct(result))
                            break
                        }

                        emit(result)
                        break
                    }

                    val inputs = decoderInput.get(
                        inputIds = beamSearch.lastTokens(),
                        cache = decoderOutput.cache
                    )

                    val outputs = decoderSession!!.run(inputs)
                    decoderInput.close()

                    decoderOutput.search(outputs)
                    decoderOutput.cache(outputs)

                    outputs.close()

                    emit(beamSearch.best())
                }
            } catch (e: Exception) {
                Timber.e(e)
                throw e
            } finally {
                decoderInput.destroy()
                decoderOutput.destroy()

            }
        }.flowOn(Dispatchers.Default)
    }

    override fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): LongArray {
        runInference = true

        // This is a workaround for the issue with various models that are overfitting on the
        // training data, and start repeating when translating single words. This is a temporary
        // solution until we can start retraining the models.
        val completeOnRepeat = inputIds.size <= 2

        val encoderHiddenStates = encode(
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        val tokens = decode(
            encoderHiddenStates = encoderHiddenStates,
            attentionMask = attentionMask,
            eosId = eosId,
            padId = padId,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            beamsSize = beamSize,
            maxSequenceLength = maxSequenceLength,
            completeOnRepeat = completeOnRepeat
        )
        return tokens
    }

    override fun runAsFlow(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<LongArray> {
        runInference = true

        // This is a workaround for the issue with various models that are overfitting on the
        // training data, and start repeating when translating single words. This is a temporary
        // solution until we can start retraining the models.
        val completeOnRepeat = inputIds.size <= 4

        val encoderHiddenStates = encode(
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        return decodeAsFlow(
            encoderHiddenStates = encoderHiddenStates,
            attentionMask = attentionMask,
            eosId = eosId,
            padId = padId,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            beamsSize = beamSize,
            maxSequenceLength = maxSequenceLength,
            completeOnRepeat = completeOnRepeat
        )
    }

    private fun distinct(tokens: LongArray): LongArray {
        val deduplicated = mutableListOf<Long>()
        var lastToken = -1L

        for (token in tokens) {
            if (token != lastToken) {
                deduplicated.add(token)
                lastToken = token
            }
        }

        return deduplicated.toLongArray()
    }

    override fun cancel() {
        runInference = false
    }

    override fun load(files: LanguageModelInferenceFiles, threads: Int) {
        close()

        val encoderFile = File(files.encoder.pathString).readBytes()
        val decoderFile = File(files.decoder.pathString).readBytes()
        val options = OrtSession.SessionOptions().apply {
            setCPUArenaAllocator(true)
            setMemoryPatternOptimization(true)
            setIntraOpNumThreads(1)
            addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            addConfigEntry("kOrtSessionOptionsConfigAllowIntraOpSpinning", "0")
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
        }

        encoderSession = ortEnvironment.createSession(encoderFile, options)
        decoderSession = ortEnvironment.createSession(decoderFile, options)
    }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()
    }

    companion object {
        private val TAG: String = MarianInference::class.java.simpleName
    }
}
