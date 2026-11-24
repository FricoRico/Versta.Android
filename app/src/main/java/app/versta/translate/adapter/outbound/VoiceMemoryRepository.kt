package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import app.versta.translate.core.entity.VoiceModelVoiceFiles
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class VoiceMemoryRepository : VoiceRepository {
    private val _mockPath = Path("")

    private val _voiceModels =
        mutableMapOf(
            Supertonic3.ID to
                VoiceWithModelFiles(
                    id = Supertonic3.ID,
                    baseModel = "Supertone/supertonic-3",
                    path = _mockPath,
                    architectures = listOf(VoiceModelArchitecture.Supertonic3),
                    version = "v2.0.0",
                    inference =
                        VoiceModelInferenceFiles(
                            models =
                                mapOf(
                                    "duration_predictor" to _mockPath,
                                    "text_encoder" to _mockPath,
                                    "vector_estimator" to _mockPath,
                                    "vocoder" to _mockPath,
                                ),
                            config = _mockPath,
                        ),
                    tokenizer =
                        VoiceModelTokenizerFiles(
                            unicodeIndexer = _mockPath,
                        ),
                    voices =
                        VoiceModelVoiceFiles().apply {
                            addAll(
                                ((1..5).map { "F$it" } + (1..5).map { "M$it" }).map {
                                    _mockPath.resolve(Supertonic3.styleFileName(it))
                                },
                            )
                        },
                ),
        )

    /**
     * Gets the voice model available in the repository.
     */
    override fun getVoiceModel(id: String) = _voiceModels[id]

    /**
     * Gets the voice models available in the repository.
     */
    override fun getVoiceModels(): Flow<List<VoiceWithModelFiles>> = flowOf(_voiceModels.values.toList())

    /**
     * Gets the voice models available in the repository by language.
     * @param language The language to filter the voice models.
     */
    override fun getVoiceModelsByLanguage(language: Language) = _voiceModels.values.first()

    /**
     * Inserts or updates the voice model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertVoiceModel(metadata: VoiceModel) {
        return
    }

    /**
     * Deletes the voice model in the repository.
     */
    override fun deleteVoiceModel(id: String) {
        _voiceModels.remove(id)
    }
}
