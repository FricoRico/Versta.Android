package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalSpeechRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalSpeechRecognitionModelWithState
import app.versta.translate.core.entity.ExternalSpeechRecognitionModels
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
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

class ExternalSpeechRecognitionModelsFileRepository(private val stream: InputStream) :
    ExternalSpeechRecognitionModelsRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableModels =
        MutableStateFlow<List<ExternalSpeechRecognitionModelDefinition>>(emptyList())

    override fun getDefinitions(): Flow<List<ExternalSpeechRecognitionModelDefinition>> {
        return _downloadableModels
    }

    override fun getDefinition(id: String): Flow<ExternalSpeechRecognitionModelDefinition> {
        return _downloadableModels.map { models -> models.first { it.id == id } }
    }

    override fun getDefinitionsByState(
        imported: Flow<List<SpeechRecognitionWithFiles>>,
    ): Flow<ExternalSpeechRecognitionModels> {
        return _downloadableModels.combine(imported) { models, installed ->
            ExternalSpeechRecognitionModels(
                installed = models.mapNotNull { model ->
                    installed.find { it.id == model.id && it.version == model.version }
                        ?.let { ExternalSpeechRecognitionModelWithState(definition = model, extracted = it.size) }
                },
                updates = models.mapNotNull { model ->
                    installed.find { it.id == model.id && it.version < model.version }
                        ?.let { ExternalSpeechRecognitionModelWithState(definition = model, extracted = it.size) }
                },
                available = models.filter { model ->
                    installed.none { it.id == model.id }
                }.map { ExternalSpeechRecognitionModelWithState(definition = it) },
            )
        }
    }

    init {
        _loadingScope.launch {
            stream.use {
                val data = it.bufferedReader().use { reader -> reader.readText() }

                val serializer = ListSerializer(ExternalSpeechRecognitionModelDefinition.serializer())
                val definitions = _serializer.decodeFromString(serializer, data)

                if (definitions.any { !it.isValid() }) {
                    throw IllegalArgumentException("Invalid speech recognition model definition")
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
