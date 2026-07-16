package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.ExternalLanguageMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ExternalLanguageModelDefinition
import app.versta.translate.core.entity.ExternalLanguageModelDefinitions
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguageModelPair
import app.versta.translate.core.entity.isValid
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

class ExternalLanguageModelsFileRepository(private val stream: InputStream) :
    ExternalLanguageModelsRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableLanguageModels =
        MutableStateFlow<List<ExternalLanguagePairDefinition>>(emptyList())

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definitions of the
     * external language models.
     */
    override fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>> {
        return _downloadableLanguageModels
    }

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definition of the
     * external language model for the given [pair].
     */
    override fun getDefinition(pair: LanguagePair): Flow<ExternalLanguagePairDefinition> {
        return _downloadableLanguageModels.map { model -> model.first { it.pair.uniqueEquals(pair) } }
    }

    /**
     * Returns a flow of [ExternalLanguageModels] that contains the definitions of the external
     * language models. These definitions are filtered by the state of the imported language models.
     */
    override fun getDefinitionsByState(imported: Flow<List<LanguageModelPair>>): Flow<ExternalLanguageModels> {
        return _downloadableLanguageModels.combine(imported) { model, existing ->
            ExternalLanguageModels(
                installed = model.mapNotNull { languages ->
                    existing.find {
                        languages.pair.uniqueEquals(it.pair) && it.files.version == languages.version
                    }?.let {
                        languages.apply {
                            extracted = it.files.size
                        }
                    }
                },
                updates = model.filter { languages ->
                    existing.find {
                        languages.pair.uniqueEquals(it.pair) && it.files.version < languages.version
                    }?.let {
                        languages.apply {
                            extracted = it.files.size
                        }
                    } != null
                },
                available = model.filter { languages ->
                    existing.none { languages.pair.uniqueEquals(it.pair) }
                }
            )
        }
    }

    private fun mapLanguageModelDefinitionToDownloadableLanguageModel(definitions: List<ExternalLanguageModelDefinitions>): List<ExternalLanguagePairDefinition> {
        if (!definitions.any { it.isValid() }) {
            throw IllegalArgumentException("Invalid language model")
        }

        return definitions.map { models ->
            val language = models.first()
            val details = models.map {
                ExternalLanguageMetadata(
                    baseModel = it.baseModel,
                    source = Language.fromIsoCode(it.sourceLanguage),
                    target = Language.fromIsoCode(it.targetLanguage),
                    score = it.score,
                    architectures = it.architectures
                )
            }

            ExternalLanguagePairDefinition(
                pair = language.pair(),
                bidirectional = language.bidirectional,
                metadata = details,
                size = language.size,
                version = language.version,
                bundleUri = language.bundleUri(),
                checksumUri = language.checksumUri(),
            )
        }
    }

    init {
        _loadingScope.launch {
            stream.use {
                val data = it.bufferedReader().use { reader ->
                    reader.readText()
                }

                val serializer =
                    ListSerializer(ListSerializer(ExternalLanguageModelDefinition.serializer()))
                val definitions = _serializer.decodeFromString(serializer, data)
                _downloadableLanguageModels.value =
                    mapLanguageModelDefinitionToDownloadableLanguageModel(definitions)
            }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val _serializer = Json { ignoreUnknownKeys = true; decodeEnumsCaseInsensitive = true }
    }
}