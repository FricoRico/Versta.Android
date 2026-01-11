package app.versta.translate.core.entity

/**
 * Font weight enumeration
 */
enum class FontWeight(val value: Int) {
    REGULAR(0),
    BOLD(1);

    companion object {
        fun fromInt(value: Int): FontWeight = when (value) {
            1 -> BOLD
            else -> REGULAR
        }
    }
}

