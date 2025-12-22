package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelWithState
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream

class ExternalObjectCharacterRecognitionModelsFileRepository(private val stream: InputStream) :
    ExternalObjectCharacterRecognitionModelsRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableModels =
        MutableStateFlow<List<ExternalObjectCharacterRecognitionModelDefinition>>(emptyList())

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definitions of the
     * external OCR models.
     */
    override fun getDefinitions(): Flow<List<ExternalObjectCharacterRecognitionModelDefinition>> {
        return _downloadableModels
    }

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definition of the
     * external OCR model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalObjectCharacterRecognitionModelDefinition> {
        return _downloadableModels.map { models -> models.first { it.id == id } }
    }

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModels] that contains the definitions of the external
     * OCR models. These definitions are filtered by the state of the imported OCR models.
     */
    override fun getDefinitionsByState(
        importedDetectors: Flow<List<ObjectCharacterRecognitionDetectorWithFiles>>,
        importedRecognizers: Flow<List<ObjectCharacterRecognitionRecognizerWithFiles>>
    ): Flow<ExternalObjectCharacterRecognitionModels> {
        return _downloadableModels.combine(importedDetectors) { models, detectors ->
            models to detectors
        }.combine(importedRecognizers) { (models, detectors), recognizers ->
            ExternalObjectCharacterRecognitionModels(
                installed = models.mapNotNull { model ->
                    val detector = detectors.find { it.id == model.id }
                    val recognizer = recognizers.find { it.id == model.id }

                    if (detector != null && recognizer != null &&
                        detector.version == model.version &&
                        recognizer.version == model.version) {
                        ExternalObjectCharacterRecognitionModelWithState(
                            definition = model,
                            extracted = calculateExtractedSize(detector, recognizer)
                        )
                    } else {
                        null
                    }
                },
                updates = models.mapNotNull { model ->
                    val detector = detectors.find { it.id == model.id }
                    val recognizer = recognizers.find { it.id == model.id }

                    if (detector != null && recognizer != null &&
                        (detector.version < model.version || recognizer.version < model.version)) {
                        ExternalObjectCharacterRecognitionModelWithState(
                            definition = model,
                            extracted = calculateExtractedSize(detector, recognizer)
                        )
                    } else {
                        null
                    }
                },
                available = models.filter { model ->
                    detectors.none { it.id == model.id } || recognizers.none { it.id == model.id }
                }.map { model ->
                    ExternalObjectCharacterRecognitionModelWithState(definition = model)
                }
            )
        }
    }

    private fun calculateExtractedSize(
        detector: ObjectCharacterRecognitionDetectorWithFiles,
        recognizer: ObjectCharacterRecognitionRecognizerWithFiles
    ): Long {
        // Calculate the size of extracted files (this is a simplified version)
        // In reality, you would traverse the directories and sum up file sizes
        return 0L // Placeholder
    }

    init {
        _loadingScope.launch {
            stream.use {
                val data = it.bufferedReader().use { reader ->
                    reader.readText()
                }

                val serializer = ListSerializer(ExternalObjectCharacterRecognitionModelDefinition.serializer())
                val definitions = _serializer.decodeFromString(serializer, data)

                if (definitions.any { !it.isValid() }) {
                    throw IllegalArgumentException("Invalid OCR model definition")
                }

                _downloadableModels.value = definitions
            }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val _serializer = Json { ignoreUnknownKeys = true; decodeEnumsCaseInsensitive = true }
    }
}

