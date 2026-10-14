package app.versta.translate.adapter.outbound

/**
 * Crash-reporting seam for events the release watch needs to see without
 * binding the app to a specific backend. Wiring a real backend (NEU-58's
 * crash watch) means swapping the implementation injected into
 * `DatabaseContainer`; call sites stay untouched.
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
