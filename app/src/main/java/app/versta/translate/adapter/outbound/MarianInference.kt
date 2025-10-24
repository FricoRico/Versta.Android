package app.versta.translate.adapter.outbound

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.bridge.inference.BeamSearch
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.MarianBatchEncoderInput
import app.versta.translate.core.entity.MarianBatchEncoderOutput
import app.versta.translate.core.entity.MarianDecoderInput
import app.versta.translate.core.entity.MarianDecoderOutput
import app.versta.translate.core.entity.EncoderAttentionMasks
import app.versta.translate.core.entity.EncoderHiddenStates
import app.versta.translate.core.entity.MarianEncoderInput
import app.versta.translate.core.entity.MarianEncoderOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.io.path.pathString

class MarianInference(private val ortEnvironment: OrtEnvironment) : TranslationInference {
    private var _encoderSessionFile: File? = null
    private var _decoderSessionFile: File? = null

    private var _encoderSession: OrtSession? = null
    private var _decoderSession: OrtSession? = null

    private var _runInference = false

    private fun encode(
        inputIds: LongArray, attentionMask: LongArray
    ): EncoderHiddenStates {
        if (_encoderSession == null) {
            throw IllegalStateException("Encoder session is not loaded")
        }

        val encoderInput = MarianEncoderInput(
            ortEnvironment = ortEnvironment,
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        val encoderOutput = MarianEncoderOutput()

        try {
            val inputs = encoderInput.get()
            val output = encoderOutput.parse(_encoderSession!!.run(inputs))

            return output ?: throw IllegalStateException("Encoder output is null")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            throw e
        } finally {
            encoderInput.destroy()
            encoderOutput.destroy()
        }
    }

    private fun encodeBatch(
        inputIds: Array<LongArray>, attentionMask: Array<LongArray>
    ): Array<EncoderHiddenStates> {
        if (_encoderSession == null) {
            throw IllegalStateException("Encoder session is not loaded")
        }

        val batchEncoderInput = MarianBatchEncoderInput(
            ortEnvironment = ortEnvironment,
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        val batchEncoderOutput = MarianBatchEncoderOutput()

        try {
            val inputs = batchEncoderInput.get()
            val output = batchEncoderOutput.parse(_encoderSession!!.run(inputs))

            return output ?: throw IllegalStateException("Encoder output is null")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            throw e
        } finally {
            batchEncoderInput.destroy()
            batchEncoderOutput.destroy()
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
        if (_decoderSession == null) {
            throw IllegalStateException("Decoder session is not loaded")
        }

        val beamSearch = BeamSearch(
            beamSize = beamsSize,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            padId = padId,
            eosId = eosId
        )

        val decoderInput = MarianDecoderInput(
            ortEnvironment = ortEnvironment,
            encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
            encoderAttentionMask = Array(beamsSize) { attentionMask }
        )

        val decoderOutput = MarianDecoderOutput(
            ortEnvironment = ortEnvironment,
            beamSearch = beamSearch
        )

        var step = 0

        try {
            while (_runInference && step < maxSequenceLength) {
                step++

                if (beamSearch.complete(completeOnRepeat)) {
                    break
                }

                val inputs = decoderInput.get(
                    inputIds = beamSearch.lastTokens(),
                    cache = decoderOutput.cache
                )

                val outputs = _decoderSession!!.run(inputs)
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
            Timber.tag(TAG).e(e)
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
        if (_decoderSession == null) {
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

            val decoderInput = MarianDecoderInput(
                ortEnvironment = ortEnvironment,
                encoderHiddenStates = Array(beamsSize) { encoderHiddenStates },
                encoderAttentionMask = Array(beamsSize) { attentionMask }
            )

            val decoderOutput = MarianDecoderOutput(
                ortEnvironment = ortEnvironment,
                beamSearch = beamSearch
            )

            var step = 0

            try {
                while (_runInference && step < maxSequenceLength) {
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

                    val outputs = _decoderSession!!.run(inputs)
                    decoderInput.close()

                    decoderOutput.search(outputs)
                    decoderOutput.cache(outputs)

                    outputs.close()

                    emit(beamSearch.best())
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
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
        _runInference = true

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
        _runInference = true

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

    override fun runBatch(
        inputIds: Array<LongArray>,
        attentionMask: Array<LongArray>,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Array<LongArray> {
        _runInference = true

        val encoderHiddenStates = encodeBatch(
            inputIds = inputIds,
            attentionMask = attentionMask
        )

        return Array(inputIds.size) { index ->
            val completeOnRepeat = inputIds[index].size <= 2

            decode(
                encoderHiddenStates = encoderHiddenStates[index],
                attentionMask = attentionMask[index],
                eosId = eosId,
                padId = padId,
                minP = minP,
                repetitionPenalty = repetitionPenalty,
                beamsSize = beamSize,
                maxSequenceLength = maxSequenceLength,
                completeOnRepeat = completeOnRepeat
            )
        }
    }

    override fun runBatchAsFlow(
        inputIds: Array<LongArray>,
        attentionMask: Array<LongArray>,
        eosId: Long,
        padId: Long,
        minP: Float,
        repetitionPenalty: Float,
        beamSize: Int,
        maxSequenceLength: Int,
    ): Flow<Array<LongArray>> {
        _runInference = true

        return flow {
            val encoderHiddenStates = encodeBatch(
                inputIds = inputIds,
                attentionMask = attentionMask
            )

            val flows = inputIds.indices.map { index ->
                val completeOnRepeat = inputIds[index].size <= 4

                decodeAsFlow(
                    encoderHiddenStates = encoderHiddenStates[index],
                    attentionMask = attentionMask[index],
                    eosId = eosId,
                    padId = padId,
                    minP = minP,
                    repetitionPenalty = repetitionPenalty,
                    beamsSize = beamSize,
                    maxSequenceLength = maxSequenceLength,
                    completeOnRepeat = completeOnRepeat
                )
            }

            // Combine all flows manually since combine has parameter limits
            if (flows.isEmpty()) {
                emit(emptyArray())
                return@flow
            }

            if (flows.size == 1) {
                flows[0].collect { result ->
                    emit(arrayOf(result))
                }
                return@flow
            }

            // For multiple flows, we use a manual combination approach
            // This collects from all flows and emits whenever any flow emits
            val currentResults = Array(flows.size) { LongArray(0) }
            val completed = BooleanArray(flows.size) { false }
            
            kotlinx.coroutines.coroutineScope {
                flows.forEachIndexed { index, flow ->
                    kotlinx.coroutines.launch {
                        flow.collect { result ->
                            currentResults[index] = result
                            emit(currentResults.clone())
                            if (result.isNotEmpty() && result.last() == eosId) {
                                completed[index] = true
                            }
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun cancel() {
        _runInference = false
    }

    override fun load(files: LanguageModelInferenceFiles, threads: Int) {
        val encoderFile = File(files.encoder.pathString)
        val decoderFile = File(files.decoder.pathString)

        if (_encoderSessionFile?.equals(encoderFile) == true && _decoderSessionFile?.equals(decoderFile) == true) {
            return
        }

        close()
        val options = OrtSession.SessionOptions().apply {
            setCPUArenaAllocator(true)
            setMemoryPatternOptimization(true)
            setIntraOpNumThreads(1)
            addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            addConfigEntry("kOrtSessionOptionsConfigAllowIntraOpSpinning", "0")
        }

        _encoderSession = ortEnvironment.createSession(readFileToByteBuffer(encoderFile), options)
        _encoderSessionFile = encoderFile

        _decoderSession = ortEnvironment.createSession(readFileToByteBuffer(decoderFile), options)
        _decoderSessionFile = decoderFile
    }

    override fun close() {
        try {
            _encoderSession?.close()
            _decoderSession?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        } finally {
            _encoderSession = null
            _encoderSessionFile = null

            _decoderSession = null
            _decoderSessionFile = null
        }
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
        private val TAG: String = MarianInference::class.java.simpleName
    }
}
