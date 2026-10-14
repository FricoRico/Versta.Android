package app.versta.translate.bridge.leanmt

import timber.log.Timber

/**
 * Reports the vendored leanmt library's binary model format version
 * (`leanmt::kBinaryFileVersion`, Versta.Leanmt `src/Io.hh`). Queried over JNI
 * rather than mirrored as a Kotlin constant so the value can never drift from
 * the actually compiled library. Stateless; construction loads the bridge
 * library but creates no native state, so it is safe to use before any model
 * load (e.g. in the bundle install pre-flight).
 */
class LeanmtVersion {
    /**
     * Binary model format version of the vendored leanmt library.
     */
    fun binaryFileVersion(): Long {
        val version = query()

        if (version <= 0L) {
            Timber.tag(TAG).w("Unexpected leanmt binary file version: $version")
        }

        return version
    }

    private external fun query(): Long

    companion object {
        private val TAG: String = LeanmtVersion::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
