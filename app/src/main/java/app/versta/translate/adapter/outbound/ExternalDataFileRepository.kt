package app.versta.translate.adapter.outbound

import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DataBundleMetadata
import app.versta.translate.core.entity.DataFilesInterface
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.ExternalDataDefinition
import app.versta.translate.core.entity.ExternalDataDefinitions
import app.versta.translate.core.entity.ExternalData
import app.versta.translate.core.entity.DataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.reflect.KClass

class ExternalDataFileRepository(private val stream: InputStream) :
    ExternalDataRepository {
    private val _loadingScope = CoroutineScope(Dispatchers.IO)

    private val _downloadableData =
        MutableStateFlow<ExternalDataDefinitions>(emptyList())

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definition of the
     * external language model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalDataDefinition> {
        return _downloadableData.map { model -> model.first { it.id == id } }
    }

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external data files.
     */
    override fun getDefinitions(): Flow<ExternalDataDefinitions> {
        return _downloadableData
    }

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external data files for the given [type].
     */
    override fun getDefinitions(type: DataType): Flow<ExternalDataDefinitions> {
        return _downloadableData.filter { it.isNotEmpty() }.map { definitions ->
            definitions.filter { it.type == type }
        }
    }

    /**
     * Returns a flow of [DataWithFiles] that contains the definition of the
     * external data definitions. These definitions are filtered by the state of the imported
     * data definitions.
     */
    override fun getDefinitionsByState(imported: Flow<List<DataWithFiles>>): Flow<ExternalData> {
        return _downloadableData.combine(imported) { model, existing ->
            ExternalData(
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

    private fun mapDataDefinitionToDownloadableVoiceModel(definitions: ExternalDataDefinitions): List<ExternalDataDefinition> {
        if (!definitions.any { it.isValid() }) {
            throw IllegalArgumentException("Invalid data definition")
        }

        return definitions
    }

    private fun mapBundleToMetadata(
        path: Path,
    ): DataBundleMetadata {
        val bundleMetadataFile = File(path.toFile(), "metadata.json")
        if (!bundleMetadataFile.exists()) {
            throw IllegalArgumentException("Data bundle metadata file not found: ${bundleMetadataFile.absolutePath}")
        }

        return _serializer.decodeFromString<DataBundleMetadata>(bundleMetadataFile.readText())
    }

    private fun mapBundleToDataFiles(
        path: Path,
    ): DataWithFiles? {
        val bundleMetadata = mapBundleToMetadata(path)

        val dataPath = path.resolve(bundleMetadata.metadata.directory)
        if (!dataPath.toFile().exists()) {
            throw IllegalArgumentException("Data directory not found: ${dataPath.absolutePathString()}")
        }

        return DataWithFiles.load(dataPath)
    }

    init {
        _loadingScope.launch {
            stream.use {
                val data = it.bufferedReader().use { reader ->
                    reader.readText()
                }

                val serializer =
                    ListSerializer(ExternalDataDefinition.serializer())
                val definitions = _serializer.decodeFromString(serializer, data)
                _downloadableData.value =
                    mapDataDefinitionToDownloadableVoiceModel(definitions)
            }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val _serializer =
            Json { ignoreUnknownKeys = true; decodeEnumsCaseInsensitive = true }
    }
}