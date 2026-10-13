package app.versta.translate.database.migrations

/**
 * Compares model version strings of the form `vMAJOR.MINOR.PATCH` numerically,
 * segment by segment, so `v1.10.0` sorts after `v1.2.0`. A leading `v` is
 * ignored and missing segments count as `0`, so `v1.2` equals `v1.2.0`.
 * Non-numeric segments fall back to lexicographic order for that segment.
 *
 * @return negative when [first] is older than [second], zero when equal,
 * positive when newer — same contract as [Comparable.compareTo].
 */
internal fun compareVersions(
    first: String,
    second: String,
): Int {
    val a = first.removePrefix("v").split('.')
    val b = second.removePrefix("v").split('.')

    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(i) ?: "0"
        val y = b.getOrNull(i) ?: "0"
        val xn = x.toIntOrNull()
        val yn = y.toIntOrNull()

        val result =
            if (xn != null && yn != null) {
                xn.compareTo(yn)
            } else {
                x.compareTo(y)
            }

        if (result != 0) {
            return result
        }
    }

    return 0
}
