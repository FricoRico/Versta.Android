package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataModel
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.TextToSpeechDataFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class DataMemoryRepository : DataRepository {
    private val _mockPath = Path("")

    /**
     * Gets a flow of [DataModel] that contains the definition of the
     * external data files. These definitions are filtered by the state of the imported
     * data.
     */
    override fun getDataByType(type: DataType): Flow<List<DataWithFiles>> {
        return flowOf(
            listOf(
                DataWithFiles(
                    id = "versta-tts-data",
                    version = "v1.0.0",
                    path = _mockPath,
                    size = 42120130,
                    files = TextToSpeechDataFiles(
                        espeak = _mockPath,
                        openJTalk = _mockPath
                    )
                )
            )
        )
    }

    /**
     * Inserts or updates the external data in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertData(metadata: DataModel) {
        return
    }

    /**
     * Delete the data of the given [type] from the repository.
     * @param type The type of the external data to delete.
     */
    override fun deleteDataByType(type: DataType) {
        return
    }
}