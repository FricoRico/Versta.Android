package app.versta.translate.adapter.outbound

import timber.log.Timber

/**
 * Default [CrashReporter]: writes a structured line through [Timber], which
 * `FileLoggingTree` persists to `app.log`, until the release watch wires a
 * real backend (NEU-58).
 */
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
