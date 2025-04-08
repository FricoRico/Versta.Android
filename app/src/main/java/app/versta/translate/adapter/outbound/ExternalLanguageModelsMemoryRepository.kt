package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExternalLanguageModelsMemoryRepository : ExternalLanguageModelsRepository {
    override fun getDefinitions(): Flow<List<ExternalLanguagePairDefinition>> {
        return flowOf(emptyList())
    }

    override fun getDefinitionsByState(availableLanguages: Flow<List<LanguagePairWithModelFiles>>): Flow<ExternalLanguageModels> {
        return flowOf(ExternalLanguageModels(
            installed = emptyList(),
            updates = emptyList(),
            available = emptyList()
        ))
    }
}