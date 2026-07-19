package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageMetadata
import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModelArchitecture
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguageModelPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.net.URI

class ExternalLanguageModelsMemoryRepository : ExternalLanguageModelsRepository {
    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definitions of the
     * external language models.
     */
    override fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>> {
        return flowOf(
            listOf(
                ExternalLanguagePairDefinition(
                    pair = LanguagePair.fromIsoCodes("en", "nl"),
                    bidirectional = true,
                    metadata = listOf(
                        ExternalLanguageMetadata(
                            baseModel = "Helsinki-NLP/opus-mt-nl-en",
                            source = Language.fromIsoCode("nl"),
                            target = Language.fromIsoCode("en"),
                            score = 60.9,
                            architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                        ), ExternalLanguageMetadata(
                            baseModel = "Neurora/opus-tatoeba-eng-nld",
                            source = Language.fromIsoCode("en"),
                            target = Language.fromIsoCode("nl"),
                            score = 57.2,
                            architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                        )
                    ),
                    size = 176478973,
                    extracted = 286893295,
                    version = "v1.1.0",
                    bundleUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz"),
                    checksumUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz.sha256"),
                ), ExternalLanguagePairDefinition(
                    pair = LanguagePair.fromIsoCodes("en", "ja"),
                    bidirectional = true,
                    metadata = listOf(
                        ExternalLanguageMetadata(
                            baseModel = "Mitsua/elan-mt-bt-ja-en",
                            source = Language.fromIsoCode("ja"),
                            target = Language.fromIsoCode("en"),
                            score = 55.0,
                            architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                        ), ExternalLanguageMetadata(
                            baseModel = "Mitsua/elan-mt-bt-en-ja",
                            source = Language.fromIsoCode("en"),
                            target = Language.fromIsoCode("ja"),
                            score = 38.4,
                            architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                        )
                    ),
                    size = 134416894,
                    version = "v1.1.0",
                    bundleUri = URI.create("https://mock.versta.app/en-ja-bundle.tar.gz"),
                    checksumUri = URI.create("https://mock.versta.app/en-ja-bundle.tar.gz.sha256"),
                )
            )
        )
    }

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definition of the
     * external language model for the given [pair].
     */
    override fun getDefinition(pair: LanguagePair): Flow<ExternalLanguagePairDefinition> {
        return flowOf(
            ExternalLanguagePairDefinition(
                pair = LanguagePair.fromIsoCodes("en", "nl"),
                bidirectional = true,
                metadata = listOf(
                    ExternalLanguageMetadata(
                        baseModel = "Helsinki-NLP/opus-mt-nl-en",
                        source = Language.fromIsoCode("nl"),
                        target = Language.fromIsoCode("en"),
                        score = 60.9,
                        architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                    ), ExternalLanguageMetadata(
                        baseModel = "Neurora/opus-tatoeba-eng-nld",
                        source = Language.fromIsoCode("en"),
                        target = Language.fromIsoCode("nl"),
                        score = 57.2,
                        architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                    )
                ),
                size = 176478973,
                extracted = 286893295,
                version = "v1.1.0",
                bundleUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz"),
                checksumUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz.sha256"),
            )
        )
    }

    /**
     * Returns a flow of [ExternalLanguageModels] that contains the definitions of the external
     * language models. These definitions are filtered by the state of the imported language models.
     */
    override fun getDefinitionsByState(imported: Flow<List<LanguageModelPair>>): Flow<ExternalLanguageModels> {
        return flowOf(
            ExternalLanguageModels(
                installed = listOf(
                    ExternalLanguagePairDefinition(
                        pair = LanguagePair.fromIsoCodes("en", "nl"),
                        bidirectional = true,
                        metadata = listOf(
                            ExternalLanguageMetadata(
                                baseModel = "Helsinki-NLP/opus-mt-nl-en",
                                source = Language.fromIsoCode("nl"),
                                target = Language.fromIsoCode("en"),
                                score = 60.9,
                                architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                            ), ExternalLanguageMetadata(
                                baseModel = "Neurora/opus-tatoeba-eng-nld",
                                source = Language.fromIsoCode("en"),
                                target = Language.fromIsoCode("nl"),
                                score = 57.2,
                                architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                            )
                        ),
                        size = 176478973,
                        extracted = 286893295,
                        version = "v1.1.0",
                        bundleUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz"),
                        checksumUri = URI.create("https://mock.versta.app/en-nl-bundle.tar.gz.sha256"),
                    ),
                ), updates = emptyList(), available = listOf(
                    ExternalLanguagePairDefinition(
                        pair = LanguagePair.fromIsoCodes("en", "ja"),
                        bidirectional = true,
                        metadata = listOf(
                            ExternalLanguageMetadata(
                                baseModel = "Mitsua/elan-mt-bt-ja-en",
                                source = Language.fromIsoCode("ja"),
                                target = Language.fromIsoCode("en"),
                                score = 55.0,
                                architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                            ), ExternalLanguageMetadata(
                                baseModel = "Mitsua/elan-mt-bt-en-ja",
                                source = Language.fromIsoCode("en"),
                                target = Language.fromIsoCode("ja"),
                                score = 38.4,
                                architectures = listOf(LanguageModelArchitecture.MarianMTModel),
                            )
                        ),
                        size = 134416894,
                        version = "v1.1.0",
                        bundleUri = URI.create("https://mock.versta.app/en-ja-bundle.tar.gz"),
                        checksumUri = URI.create("https://mock.versta.app/en-ja-bundle.tar.gz.sha256"),
                    )
                )
            )
        )
    }
}