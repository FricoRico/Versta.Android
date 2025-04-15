package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import kotlinx.coroutines.flow.Flow

interface ExternalLanguageModelsRepository {
    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definitions of the
     * external language models.
     */
    fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>>

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definition of the
     * external language model for the given [pair].
     */
    fun getDefinition(pair: LanguagePair): Flow<ExternalLanguagePairDefinition>

    /**
     * Returns a flow of [ExternalLanguageModels] that contains the definitions of the external
     * language models. These definitions are filtered by the state of the imported language pairs.
     */
    fun getDefinitionsByState(availableLanguages: Flow<List<LanguagePairWithModelFiles>>): Flow<ExternalLanguageModels>
}