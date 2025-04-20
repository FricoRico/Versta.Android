package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.ExternalVoiceModelDefinitions
import app.versta.translate.core.entity.ExternalVoiceModels
import app.versta.translate.core.entity.VoiceWithModelFiles
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

class ExternalVoiceModelsFileRepository(private val stream: InputStream) :
    ExternalVoiceModelsRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableVoiceModels =
        MutableStateFlow<ExternalVoiceModelDefinitions>(emptyList())

    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definitions of the
     * external text-to-speech models.
     */
    override fun getDefinitions(): Flow<ExternalVoiceModelDefinitions> {
        return _downloadableVoiceModels
    }

    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definition of the
     * external language model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalVoiceModelDefinition> {
        return _downloadableVoiceModels.map { model -> model.first { it.id == id } }
    }

    /**
     * Returns a flow of [VoiceWithModelFiles] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    override fun getDefinitionsByState(imported: Flow<List<VoiceWithModelFiles>>): Flow<ExternalVoiceModels> {
        return _downloadableVoiceModels.combine(imported) { model, existing ->
            ExternalVoiceModels(
                installed = model.mapNotNull { speech ->
                    existing.find {
                        speech.id == it.id && it.version == speech.version
                    }?.let {
                        speech.apply {
                            extracted = it.size
                        }
                    }
                },
                updates = model.filter { speech ->
                    existing.find {
                        speech.id == it.id && it.version < speech.version
                    }?.let {
                        speech.apply {
                            extracted = it.size
                        }
                    } != null
                },
                available = model.filter { speech ->
                    existing.none { speech.id == it.id }
                }
            )
        }
    }

    private fun mapVoiceModelDefinitionToDownloadableVoiceModel(definitions: ExternalVoiceModelDefinitions): List<ExternalVoiceModelDefinition> {
        if (!definitions.any { it.isValid() }) {
            throw IllegalArgumentException("Invalid text-to-speech model")
        }

        return definitions
    }

    init {
        _loadingScope.launch {
            stream.use {
                val data = it.bufferedReader().use { reader ->
                    reader.readText()
                }

                val serializer =
                    ListSerializer(ExternalVoiceModelDefinition.serializer())
                val definitions = _serializer.decodeFromString(serializer, data)
                _downloadableVoiceModels.value =
                    mapVoiceModelDefinitionToDownloadableVoiceModel(definitions)
            }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val _serializer = Json { ignoreUnknownKeys = true; decodeEnumsCaseInsensitive = true }
    }
}