package app.versta.translate.adapter.outbound

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.util.Log
import app.versta.translate.core.entity.BeamSearch
import app.versta.translate.core.entity.DecoderCache
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.DecoderInput
import app.versta.translate.core.entity.DecoderOutput
import app.versta.translate.core.entity.EncoderAttentionMasks
import app.versta.translate.core.entity.EncoderHiddenStates
import app.versta.translate.core.entity.EncoderInput
import app.versta.translate.core.entity.EncoderOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

        val beams = BeamSearch(
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
        val tempCacheTensors = mutableListOf<OnnxTensor>()

        try {
            for (step in 0 until maxSequenceLength) {
                if (beams.complete()) {
                    break
                }

                val inputIds = beams.lastTokens()
                val cache = decoderOutput.cache.map { (key, value) ->
                    val shape = value.info.shape
                    val buffer = value.floatBuffer
                    val count = value.info.numElements.toInt()

                    val transposed = beams.transposeCache(shape, buffer, count)
                    val tensor = OnnxTensor.createTensor(ortEnvironment, transposed, shape)

                    tempCacheTensors.add(tensor)

                    key to tensor
                }.toMap()

                val inputs = decoderInput.get(
                    inputIds = inputIds,
                    cache = cache
                )
                val output = decoderOutput.parse(decoderSession!!.run(inputs)) ?: break

                beams.search(
                    logits = output,
                    size = beamsSize,
                )
            }

            return beams.best()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            Log.d(TAG, "Closing all caches including decoder cache size: ${tempCacheTensors.size}")
            decoderInput.close()
            decoderOutput.close()
            tempCacheTensors.forEach { it.close() }
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
            val beams = BeamSearch(
                size = beamsSize,
                padId = padId,
                eosId = eosId,
            )

            val decoderInput = DecoderInput(
                ortEnvironment = ortEnvironment,
                encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
                encoderAttentionMask = Array(beamsSize) { attentionMask }
            )

            val decoderOutput = DecoderOutput()
            val tempCacheTensors = mutableListOf<OnnxTensor>()

            try {
                for (step in 0 until maxSequenceLength) {
                    if (beams.complete()) {
                        break
                    }

                    val inputIds = beams.lastTokens()
                    val cache = transposeDecoderCache(decoderOutput.cache, beams)
                    tempCacheTensors.forEach { it.close() }
                    tempCacheTensors.clear()

                    tempCacheTensors.addAll(cache.values)

                    val inputs = decoderInput.get(
                        inputIds = inputIds,
                        cache = cache
                    )
                    val output =
                        decoderOutput.parse(decoderSession!!.run(inputs)) ?: break

                    beams.search(
                        logits = output,
                        size = beamsSize,
                    )

                    emit(beams.best())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            } finally {
                Log.d(TAG, "Closing all caches including decoder cache size: ${tempCacheTensors.size}")
                decoderInput.close()
                decoderOutput.close()
                tempCacheTensors.forEach { it.close() }
                tempCacheTensors.clear()
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

    private suspend fun transposeDecoderCache(
        cache: DecoderCache,
        beams: BeamSearch,
    ): Map<String, OnnxTensor> = coroutineScope {
        return@coroutineScope cache.map { (key, value) ->
            async(Dispatchers.Default) {
                val shape = value.info.shape
                val buffer = value.floatBuffer
                val count = value.info.numElements.toInt()

                val transposed = beams.transposeCache(shape, buffer, count)

                transposed.clear()

                key to OnnxTensor.createTensor(ortEnvironment, transposed, shape)
            }
        }.awaitAll().toMap()
    }

    override fun load(files: LanguageModelInferenceFiles) {
        close()

        val encoderFile = File(files.encoder.pathString).readBytes()
        val decoderFile = File(files.decoder.pathString).readBytes()

        encoderSession = ortEnvironment.createSession(encoderFile, sessionOptions)
        decoderSession = ortEnvironment.createSession(decoderFile, sessionOptions)
    }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()

        ortEnvironment.close()
    }

    companion object {
        private val TAG: String = MarianInference::class.java.simpleName
    }
}
