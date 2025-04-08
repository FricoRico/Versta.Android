package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.ExternalLanguageMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ExternalLanguageModelDefinition
import app.versta.translate.core.entity.ExternalLanguageModelDefinitions
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import app.versta.translate.core.entity.isValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream

class ExternalLanguageModelsFileRepository(private val stream: InputStream) :
    ExternalLanguageModelsRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableLanguageModels =
        MutableStateFlow<List<ExternalLanguagePairDefinition>>(emptyList())

    override fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>> {
        return _downloadableLanguageModels
    }

    override fun getDefinitionsByState(availableLanguages: Flow<List<LanguagePairWithModelFiles>>): Flow<ExternalLanguageModels> {
        return _downloadableLanguageModels.combine(availableLanguages) { model, imported ->
            ExternalLanguageModels(
                installed = model.mapNotNull { languages ->
                    imported.find {
                        languages.pair.uniqueEquals(it.pair) && it.files.version == languages.version
                    }?.let {
                        languages.apply {
                            extracted = it.files.size
                        }
                    }
                },
                updates = model.filter { languages ->
                    imported.any {
                        languages.pair.uniqueEquals(it.pair) && it.files.version < languages.version
                    }
                },
                available = model.filter { languages ->
                    imported.none { languages.pair.uniqueEquals(it.pair) }
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
        private val _serializer = Json { ignoreUnknownKeys = true }
    }
}