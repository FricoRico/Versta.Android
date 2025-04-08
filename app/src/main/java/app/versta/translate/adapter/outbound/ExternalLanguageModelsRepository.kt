package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import kotlinx.coroutines.flow.Flow

interface ExternalLanguageModelsRepository {
    fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>>

    fun getDefinitionsByState(availableLanguages: Flow<List<LanguagePairWithModelFiles>>): Flow<ExternalLanguageModels>
}