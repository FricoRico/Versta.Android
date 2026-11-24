package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.leanmt.Leanmt
import app.versta.translate.bridge.leanmt.TranslationEngine

/**
 * Owns construction of the native translation engines behind the
 * [TranslationInference] port: the only place in the app layer that names the
 * leanmt bridge. The composition root ([app.versta.translate.ApplicationModule])
 * holds one instance; ViewModels and the
 * [app.versta.translate.core.model.TranslationOrchestrator] only ever see the
 * port (ADR-0001, R1.5).
 *
 * Engine topology is unchanged: the initial intermediate/output pair shares
 * one cached native service sized with [DEFAULT_CACHE_SIZE], while engines
 * recreated to apply a new cache size each get a fresh service. Model
 * load/unload stays behind the port and remains orchestrated by the caller.
 */
class TranslationEngineManager(
    defaultCacheSize: Long = DEFAULT_CACHE_SIZE.toLong(),
    private val createService: (cacheSize: Long) -> TranslationEngine = { Leanmt(it) },
) {
    private val sharedService: TranslationEngine by lazy { createService(defaultCacheSize) }

    val intermediateModel: TranslationInference by lazy { BergamotTinyInference(sharedService) }
    val outputModel: TranslationInference by lazy { BergamotTinyInference(sharedService) }

    /**
     * Creates a fresh inference backed by its own native service with the
     * given translation cache size, used when a changed cache configuration
     * has to take effect (leanmt fixes the cache size at engine construction).
     */
    fun createInference(cacheSize: Long): TranslationInference = BergamotTinyInference(createService(cacheSize))
}
