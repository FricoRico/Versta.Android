package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.ExternalVoiceModelDefinitions
import app.versta.translate.core.entity.ExternalVoiceModels
import app.versta.translate.core.entity.ExternalVoice
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExternalVoiceModelsMemoryRepository : ExternalVoiceModelsRepository {
    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definitions of the
     * external text-to-speech models.
     */
    override fun getDefinitions(): Flow<ExternalVoiceModelDefinitions> {
        return flowOf(
            listOf(
                ExternalVoiceModelDefinition(
                    id = "kokoro",
                    name = "Kokoro",
                    baseModel = "hexgrad/Kokoro-82M",
                    version = "v1.0.0",
                    size = 163899505,
                    voices = listOf(
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "en",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "en"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "ja",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "ja"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "zh",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "zh"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "es",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "es"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "pt",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "pt"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "hi",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "hi"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "it",
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Male,
                            language = "it"
                        ),
                        ExternalVoice(
                            gender = VoiceGender.Female,
                            language = "fr",
                        )
                    ),
                    architectures = listOf(VoiceModelArchitecture.StyleTTS2),
                    bundle = "https://mock.versta.app/kokoro-bundle.tar.gz",
                    checksum = "https://mock.versta.app/kokoro-bundle.tar.gz.sha256",
                )
            )
        )
    }

    /**
     * Returns a flow of [ExternalVoiceModelDefinition] that contains the definition of the
     * external language model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalVoiceModelDefinition> {
        return flowOf(
            ExternalVoiceModelDefinition(
                id = "kokoro",
                name = "Kokoro",
                baseModel = "hexgrad/Kokoro-82M",
                version = "v1.0.0",
                size = 163899505,
                voices = listOf(
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "en",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "en"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "ja",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "ja"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "zh",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "zh"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "es",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "es"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "pt",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "pt"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "hi",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "hi"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "it",
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Male,
                        language = "it"
                    ),
                    ExternalVoice(
                        gender = VoiceGender.Female,
                        language = "fr",
                    )
                ),
                architectures = listOf(VoiceModelArchitecture.StyleTTS2),
                bundle = "https://mock.versta.app/kokoro-bundle.tar.gz",
                checksum = "https://mock.versta.app/kokoro-bundle.tar.gz.sha256",
            )
        )
    }

    /**
     * Returns a flow of [ExternalVoiceModels] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    override fun getDefinitionsByState(imported: Flow<List<VoiceWithModelFiles>>): Flow<ExternalVoiceModels> {
        return flowOf(
            ExternalVoiceModels(
                installed = emptyList(),
                updates = emptyList(),
                available = listOf(
                    ExternalVoiceModelDefinition(
                        id = "kokoro",
                        name = "Kokoro",
                        baseModel = "hexgrad/Kokoro-82M",
                        version = "v1.0.0",
                        size = 163899505,
                        voices = listOf(
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "en",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "en"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "ja",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "ja"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "zh",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "zh"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "es",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "es"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "pt",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "pt"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "hi",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "hi"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "it",
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Male,
                                language = "it"
                            ),
                            ExternalVoice(
                                gender = VoiceGender.Female,
                                language = "fr",
                            )
                        ),
                        architectures = listOf(VoiceModelArchitecture.StyleTTS2),
                        bundle = "https://mock.versta.app/kokoro-bundle.tar.gz",
                        checksum = "https://mock.versta.app/kokoro-bundle.tar.gz.sha256",
                    )
                )
            )
        )
    }
}