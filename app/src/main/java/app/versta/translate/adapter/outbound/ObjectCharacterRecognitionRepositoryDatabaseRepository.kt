package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleWithFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.OcrModuleModel as OcrModuleDatabaseModel

class ObjectCharacterRecognitionRepositoryDatabaseRepository(
    private val database: DatabaseContainer
) : ObjectCharacterRecognitionRepository {
    override fun getModule(id: String): ObjectCharacterRecognitionModuleWithFiles? {
        return mapObjectCharacterRecognitionModuleDatabaseModelToObjectCharacterRecognitionModuleFiles(
            database.ocrModules.getModuleById(id).executeAsOneOrNull()
        )
    }

    override fun getModules() =
        database.ocrModules.getAllModules().executeAsListFlow()
            .map { it.mapNotNull { module -> mapObjectCharacterRecognitionModuleDatabaseModelToObjectCharacterRecognitionModuleFiles(module) } }

    override fun getCompleteBundle(): ObjectCharacterRecognitionBundleWithFiles? {
        val modules = database.ocrModules.getAllModules().executeAsList()
            .mapNotNull { mapObjectCharacterRecognitionModuleDatabaseModelToObjectCharacterRecognitionModuleFiles(it) }

        return modules
            .groupBy { it.bundleId }
            .values
            .mapNotNull { bundleModules ->
                ObjectCharacterRecognitionBundleWithFiles.load(
                    id = bundleModules.first().bundleId,
                    path = bundleModules.first().path.parent ?: return@mapNotNull null,
                    modules = bundleModules
                )
            }
            .firstOrNull { it.isComplete }
    }

    override fun getRecognizerForLanguage(language: Language): ObjectCharacterRecognitionModuleWithFiles? {
        return getCompleteBundle()?.recognizerForLanguage(language.isoCode)
    }

    override fun upsertModule(metadata: ObjectCharacterRecognitionModuleModel) {
        upsertObjectCharacterRecognitionModule(
            mapObjectCharacterRecognitionModuleMetadataToObjectCharacterRecognitionModuleDatabaseModel(
                data = metadata
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override fun deleteBundle(id: String) {
        database.ocrModules.getModulesByBundleId(id).executeAsList()
            .mapNotNull { it.path.toPath().toNioPath().parent }.distinct()
            .forEach { it.deleteRecursively() }

        database.ocrModules.deleteModulesByBundleId(bundleId = id)
    }

    private fun upsertObjectCharacterRecognitionModule(data: OcrModuleDatabaseModel) {
        database.ocrModules.upsertModule(
            id = data.id,
            bundleId = data.bundleId,
            module = data.module,
            directory = data.directory,
            languages = data.languages,
            path = data.path,
            version = data.version
        )
    }

    private fun mapObjectCharacterRecognitionModuleMetadataToObjectCharacterRecognitionModuleDatabaseModel(
        data: ObjectCharacterRecognitionModuleModel
    ): OcrModuleDatabaseModel {
        return OcrModuleDatabaseModel(
            id = data.id,
            bundleId = data.bundle.id,
            module = data.model.module.name,
            directory = data.directory,
            languages = data.model.languages,
            path = data.root?.absolutePathString() ?: "",
            version = data.bundle.version
        )
    }

    private fun mapObjectCharacterRecognitionModuleDatabaseModelToObjectCharacterRecognitionModuleFiles(data: OcrModuleDatabaseModel?): ObjectCharacterRecognitionModuleWithFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return ObjectCharacterRecognitionModuleWithFiles.load(
                id = data.id,
                bundleId = data.bundleId,
                version = data.version,
                path = path
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load OCR module files: ${data.id}")
            return null
        }
    }

    companion object {
        private val TAG: String =
            ObjectCharacterRecognitionRepositoryDatabaseRepository::class.java.simpleName
    }
}
