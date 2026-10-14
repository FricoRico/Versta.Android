package app.versta.translate.core.entity

/**
 * Raised while installing a language model bundle whose declared
 * `leanmt_binary_version` is not compatible with the vendored leanmt's
 * `kBinaryFileVersion` (Versta.Leanmt `src/Io.hh`). The contract is defined
 * in Versta.Models `translation/PIPELINE.md` ("leanmt handshake"); the gate
 * exists so a stale or foreign bundle fails with a clean error at install
 * time instead of surfacing leanmt's fail-fast native error at first use.
 *
 * @property declared the bundle manifest's `leanmt_binary_version`, or null
 * when the bundle pre-dates v2.1.0 and does not declare the field.
 * @property supported the vendored leanmt library's binary format version.
 */
class IncompatibleModelBundleException(
    val declared: Long?,
    val supported: Long,
) : Exception(
        if (declared == null) {
            "Model bundle does not declare leanmt_binary_version (supported: $supported)"
        } else {
            "Model bundle declares leanmt_binary_version $declared (supported: $supported)"
        },
    ) {
    /**
     * Why the bundle is rejected; decides which user-facing message to show.
     */
    val reason: Reason
        get() =
            when {
                declared == null -> Reason.BundleUndeclared
                declared > supported -> Reason.AppOutdated
                else -> Reason.BundleOutdated
            }

    enum class Reason {
        /** Pre-v2.1.0 bundle without `leanmt_binary_version`; needs a refreshed bundle. */
        BundleUndeclared,

        /** Bundle declares an older binary version than the app supports; needs a refreshed bundle. */
        BundleOutdated,

        /** Bundle declares a newer binary version than the app supports; needs an app update. */
        AppOutdated,
    }
}
