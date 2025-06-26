package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataModel
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.DataWithFiles
import kotlinx.coroutines.flow.Flow

interface DataRepository {
    /**
     * Gets a flow of [DataModel] that contains the definition of the
     * external data files. These definitions are filtered by the state of the imported
     * data.
     */
    fun getDataByType(type: DataType): Flow<List<DataWithFiles>>

    /**
     * Inserts or updates the external data in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertData(metadata: DataModel)

    /**
     * Delete the data of the given [type] from the repository.
     * @param type The type of the external data to delete.
     */
    fun deleteDataByType(type: DataType)
}