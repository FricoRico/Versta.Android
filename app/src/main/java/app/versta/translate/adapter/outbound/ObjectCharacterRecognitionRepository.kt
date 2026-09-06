package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleWithFiles
import kotlinx.coroutines.flow.Flow

interface ObjectCharacterRecognitionRepository {
    /**
     * Gets the OCR module by its [id] (`bundleId:directory`).
     */
    fun getModule(id: String): ObjectCharacterRecognitionModuleWithFiles?

    /**
     * Gets all installed OCR modules.
     */
    fun getModules(): Flow<List<ObjectCharacterRecognitionModuleWithFiles>>

    /**
     * Gets the first complete installed bundle (detector + at least one recognizer).
     */
    fun getCompleteBundle(): ObjectCharacterRecognitionBundleWithFiles?

    fun getRecognizerForLanguage(language: Language): ObjectCharacterRecognitionModuleWithFiles?

    /**
     * Inserts or updates an OCR module.
     * @param metadata The metadata to insert or update.
     */
    fun upsertModule(metadata: ObjectCharacterRecognitionModuleModel)

    /**
     * Deletes all modules of a bundle and their files.
     */
    fun deleteBundle(id: String)
}
