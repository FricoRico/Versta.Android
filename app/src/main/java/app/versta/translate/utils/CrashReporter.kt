package app.versta.translate.utils

import timber.log.Timber

/**
 * Crash-reporting seam for events the release watch needs to see without
 * binding the app to a specific backend. The default implementation writes a
 * structured line through [Timber], which [FileLoggingTree] persists to
 * `app.log`; wiring a real backend (NEU-58's crash watch) means swapping this
 * one implementation, call sites stay untouched.
 */
interface CrashReporter {
    /**
     * Emits a named event with numeric attributes, mirroring the shape crash
     * reporters expose for custom keys.
     */
    fun recordEvent(
        name: String,
        attributes: Map<String, Long>,
    )
}

object TimberCrashReporter : CrashReporter {
    private const val TAG = "CrashReporting"

    override fun recordEvent(
        name: String,
        attributes: Map<String, Long>,
    ) {
        val payload = attributes.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        Timber.tag(TAG).i("$name $payload")
    }
}
