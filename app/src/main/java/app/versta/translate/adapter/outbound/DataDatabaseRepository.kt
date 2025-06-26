package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataModel
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.collections.distinct
import kotlin.collections.map
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.Data as DataDatabaseModel

@OptIn(ExperimentalPathApi::class)
class DataDatabaseRepository(
    private val database: DatabaseContainer,
) : DataRepository {
    /**
     * Gets a flow of [DataModel] that contains the definition of the
     * external data files. These definitions are filtered by the state of the imported
     * data.
     */
    override fun getDataByType(type: DataType) =
        database.data.getByType(type.value).executeAsListFlow()
            .map { it.mapNotNull { data -> mapDataDatabaseModelToDataWithFiles(data) } }

    /**
     * Inserts or updates the external data in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertData(metadata: DataModel) {
        val dataModel = mapDataModelToDataDatabaseModel(metadata)
        upsertData(data = dataModel)
    }

    /**
     * Delete the data of the given [type] from the repository.
     * @param type The type of the external data to delete.
     */
    override fun deleteDataByType(type: DataType) {
        database.data.getByType(type.value).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.data.deleteByType(type.value)
    }

    /**
     * Inserts or updates the data in the database.
     * @param data The data to insert or update.
     */
    private fun upsertData(data: DataDatabaseModel) {
        database.data.upsert(
            id = data.id,
            type = data.type,
            path = data.path,
            version = data.version,
        )
    }

    /**
     * Maps the [DataModel] to the [DataDatabaseModel].
     * @param data The data to map.
     * @return The mapped data.
     */
    private fun mapDataModelToDataDatabaseModel(data: DataModel): DataDatabaseModel {
        return DataDatabaseModel(
            id = data.id,
            type = data.contents.type,
            path = data.contents.root?.absolutePathString() ?: "",
            version = data.contents.version,
        )
    }

    /**
     * Maps the [DataDatabaseModel] to the [DataWithFiles].
     * @param data The data to map.
     * @return The mapped data or null if an error occurs.
     */
    private fun mapDataDatabaseModelToDataWithFiles(data: DataDatabaseModel): DataWithFiles? {
        val path = data.path.toPath().toNioPath()

        try {
            return DataWithFiles.load(path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading data from path: ${data.path}")
            return null
        }
    }

    companion object {
        private val TAG: String = DataDatabaseRepository::class.java.simpleName
    }
}