package app.versta.translate.adapter.outbound

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.extensions.OrtxPackage
import android.util.Log
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.model.ModelInterface
import app.versta.translate.bridge.inference.BeamSearch
import java.io.File
import kotlin.io.path.pathString
import java.util.PriorityQueue
import kotlin.math.ln

// Shape: [batch_size, sequence_length, hidden_size]
typealias EncoderHiddenStates = Array<Array<FloatArray>>

// Shape: [batch_size, sequence_length, vocab_size]
typealias DecoderLogits = Array<Array<FloatArray>>

class MarianInference(
    threadCount: Int = 4,
) : ModelInterface {
    private val ortEnvironment = OrtEnvironment.getEnvironment(
        OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING,
        "OpusInference",
        OrtEnvironment.ThreadingOptions().apply {
            setGlobalSpinControl(false)
        })

    private val sessionOptions = OrtSession.SessionOptions().apply {
        setCPUArenaAllocator(true)
        setMemoryPatternOptimization(true)
        setInterOpNumThreads(1)
        addXnnpack(mapOf("intra_op_num_threads" to threadCount.toString()))
        registerCustomOpLibrary(OrtxPackage.getLibraryPath())
    }

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    @Suppress("UNCHECKED_CAST")
    private fun encode(
        inputIds: LongArray,
        attentionMask: LongArray
    ): Array<FloatArray> {
        val inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, arrayOf(inputIds))
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, arrayOf(attentionMask))

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        try {
            val encoderOutput = encoderSession?.run(inputs)

            if (encoderOutput == null) {
                inputIdsTensor.close()
                attentionMaskTensor.close()
                return emptyArray()
            }

            val encoderHiddenStates =
                encoderOutput.get("last_hidden_state").get().value as EncoderHiddenStates
            encoderOutput.close()

            return encoderHiddenStates.first()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
        }
    }

    data class Beam(val sequence: LongArray, val score: Float)

    @Suppress("UNCHECKED_CAST")
    private suspend fun beamDecode(
        encoderHiddenStates: Array<FloatArray>,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beamsSize: Int,
        maxSequenceLength: Int
    ): LongArray {
        try {
            var beams: MutableList<Beam> = MutableList(beamsSize) {
                Beam(longArrayOf(padId), 1f)
            }
            val encoderHiddenStateBeams = Array(beamsSize) { encoderHiddenStates }
            val attentionMaskBeams = Array(beamsSize) { attentionMask }

            val encoderOutputsTensor =
                OnnxTensor.createTensor(ortEnvironment, encoderHiddenStateBeams)
            val encoderAttentionMaskTensor =
                OnnxTensor.createTensor(ortEnvironment, attentionMaskBeams)

            val noCacheTensor = OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(false))
            val cacheTensor = OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(true))

            val inputs = mutableMapOf<String, OnnxTensorLike>(
                "encoder_hidden_states" to encoderOutputsTensor,
                "encoder_attention_mask" to encoderAttentionMaskTensor,
                "use_cache_branch" to noCacheTensor
            )

            var decoderOutputs: OrtSession.Result? = null

            var stepsMade = 0
            var totalInferenceTime = 0L
            var startTime: Long

            val presentDecoderRegex = "present.\\d".toRegex()
            val decoderCache = mutableMapOf<String, OnnxTensorLike>()

            for (step in 0 until maxSequenceLength) {
                if (beams.all { it.sequence[it.sequence.size - 1] == eosId }) {
                    break
                }

                val decoderInputIds = Array(beamsSize) { i ->
                    longArrayOf(beams[i].sequence.last())
                }
                val decoderInputIdsTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    decoderInputIds
                )
                inputs["input_ids"] = decoderInputIdsTensor

                if (decoderCache.isNotEmpty()) {
                    decoderCache.forEach { (key, value) ->
                        inputs[key] = value
                    }
                    inputs["use_cache_branch"] = cacheTensor
                }

                startTime = System.currentTimeMillis()
                decoderOutputs = decoderSession?.run(inputs)
                totalInferenceTime += System.currentTimeMillis() - startTime
                stepsMade++

                Log.i(
                    "OpusInference",
                    "Inference time: ${System.currentTimeMillis() - startTime} ms, current step: $step"
                )

                if (decoderOutputs == null) {
                    decoderInputIdsTensor.close()
                    break
                }

                for (output in decoderOutputs) {
                    when {
                        output.key == "logits" -> {
                            val logits = output.value.value as DecoderLogits

                            startTime = System.currentTimeMillis()
                            // Parallel processing of beams using Kotlin coroutines
                            var newBeams = arrayOf<Beam>()
                            for (i in beams.indices) {
                                // Retrieve top-k tokens for each beam independently
                                val topKIndices = BeamSearch.topKIndices(logits[i][0], 8)

                                // Expand each beam with top-k tokens
                                for (token in topKIndices) {
                                    val newSeq = beams[i].sequence + token.toLong()
                                    val logitValue = logits[i][0][token].coerceAtLeast(1e-9f)
                                    val newScore = beams[i].score + ln(logitValue)

                                    newBeams = newBeams.plus(Beam(newSeq, newScore))
                                }
                            }

                            Log.i(
                                "OpusInference",
                                "Beam search time: ${System.currentTimeMillis() - startTime} ms"
                            )

                            // Prune: Keep only the top `beamsSize` beams by score
                            beams = newBeams
                                .sortedByDescending { it.score }
                                .take(beamsSize)
                                .toMutableList()
                        }

                        output.key.contains(
                            presentDecoderRegex
                        ) -> {
                            if (output.value.info is TensorInfo) {
                                val info = output.value.info as TensorInfo

                                if (info.shape[2] >= 512) {
                                    decoderCache.clear()
                                    continue
                                }

                                if (info.shape[0] == 0L) {
                                    continue
                                }
                            }

                            val key = output.key.replace("present", "past_key_values")
                            if (output.value is OnnxTensorLike) {
//                                decoderCache[key]?.close()
                                decoderCache[key] = output.value as OnnxTensorLike
                            }
                        }

                        else -> {
                            continue
                        }
                    }
                }

                val tempDecoderOutputResults = beams.maxByOrNull { it.score }?.sequence ?: longArrayOf()
                Log.d(TAG, "Best sequence: ${tempDecoderOutputResults.joinToString(" ")}")

                decoderInputIdsTensor.close()
            }

            decoderOutputs?.close()

            noCacheTensor.close()
            cacheTensor.close()
            encoderOutputsTensor.close()
            encoderAttentionMaskTensor.close()

            Log.i(
                "OpusInference",
                "Total inference time: $totalInferenceTime ms, in $stepsMade steps"
            )

            return beams.maxByOrNull { it.score }?.sequence ?: longArrayOf()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun run(
        inputIds: LongArray,
        attentionMask: LongArray,
        eosId: Long,
        padId: Long,
        beams: Int,
        maxSequenceLength: Int
    ): LongArray {
        val encoderHiddenStates = encode(inputIds, attentionMask)

        return beamDecode(
            encoderHiddenStates,
            attentionMask,
            eosId,
            padId,
            beams,
            maxSequenceLength
        )
    }

    // Efficient top-k selection without sorting entire array
    private fun getTopKIndices(array: FloatArray, k: Int): LongArray {
        val heap = PriorityQueue<Pair<Int, Float>>(compareByDescending { it.second })
        for (i in array.indices) {
            val value = array[i]
            if (heap.size < k) {
                heap.add(Pair(i, value))
            } else if (value > (heap.peek()?.second ?: 0f)) {
                heap.poll()
                heap.add(Pair(i, value))
            }
        }
        return heap.map { it.first.toLong() }.toLongArray()
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
