package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow

interface TranslationPreferenceRepository {
    fun setCacheSize(size: Int)

    fun getCacheSize(): Flow<Int>

    fun setCacheEnabled(enabled: Boolean)

    fun setNumberOfBeams(beams: Int)

    fun getNumberOfBeams(): Flow<Int>
}