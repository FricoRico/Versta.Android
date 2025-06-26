package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataFilesInterface
import app.versta.translate.core.entity.ExternalDataDefinition
import app.versta.translate.core.entity.ExternalDataDefinitions
import app.versta.translate.core.entity.ExternalData
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.TextToSpeechDataFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.reflect.KClass

class ExternalDataMemoryRepository : ExternalDataRepository {
    private val _mockPath = Path("")

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external text-to-speech models.
     */
    override fun getDefinitions(): Flow<ExternalDataDefinitions> {
        return flowOf(
            listOf(
                ExternalDataDefinition(
                    id = "versta-tts-data",
                    version = "v1.0.0",
                    type = DataType.TTS,
                    size = 42120130,
                    bundle = "https://mock.versta.app/versta-tts-data-bundle.tar.gz",
                    checksum = "https://mock.versta.app/versta-tts-data-bundle.tar.gz.sha256",
                )
            )
        )
    }

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external data files for the given [type].
     */
    override fun getDefinitions(type: DataType): Flow<ExternalDataDefinitions> {
        return flowOf(
            listOf(
                ExternalDataDefinition(
                    id = "versta-tts-data",
                    version = "v1.0.0",
                    type = DataType.TTS,
                    size = 42120130,
                    bundle = "https://mock.versta.app/versta-tts-data-bundle.tar.gz",
                    checksum = "https://mock.versta.app/versta-tts-data-bundle.tar.gz.sha256",
                )
            )
        )
    }

    /**
     * Returns a flow of [ExternalDataDefinition] that contains the definition of the
     * external language model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalDataDefinition> {
        return flowOf(
            ExternalDataDefinition(
                id = "versta-tts-data",
                version = "v1.0.0",
                type = DataType.TTS,
                size = 42120130,
                bundle = "https://mock.versta.app/versta-tts-data-bundle.tar.gz",
                checksum = "https://mock.versta.app/versta-tts-data-bundle.tar.gz.sha256",
            )
        )
    }

    /**
     * Returns a flow of [ExternalData] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    override fun getDefinitionsByState(imported: Flow<List<DataWithFiles>>): Flow<ExternalData> {
        return flowOf(
            ExternalData(
                installed = emptyList(),
                updates = emptyList(),
                available = listOf(
                    ExternalDataDefinition(
                        id = "versta-tts-data",
                        version = "v1.0.0",
                        type = DataType.TTS,
                        size = 42120130,
                        bundle = "https://mock.versta.app/versta-tts-data-bundle.tar.gz",
                        checksum = "https://mock.versta.app/versta-tts-data-bundle.tar.gz.sha256",
                    )
                )
            )
        )
    }
}