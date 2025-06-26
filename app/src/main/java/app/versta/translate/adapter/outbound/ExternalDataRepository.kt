package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataFilesInterface
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.ExternalDataDefinition
import app.versta.translate.core.entity.ExternalDataDefinitions
import app.versta.translate.core.entity.ExternalData
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import kotlin.reflect.KClass

interface ExternalDataRepository {
    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definition of the
     * external data file for the given [id].
     */
    fun getDefinition(id: String): Flow<ExternalDataDefinition>

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external data files.
     */
    fun getDefinitions(): Flow<ExternalDataDefinitions>

    /**
     * Returns a flow of [ExternalDataDefinitions] that contains the definitions of the
     * external data files for the given [type].
     */
    fun getDefinitions(type: DataType): Flow<ExternalDataDefinitions>

    /**
     * Returns a flow of [VoiceWithModelFiles] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    fun getDefinitionsByState(imported: Flow<List<DataWithFiles>>): Flow<ExternalData>
}