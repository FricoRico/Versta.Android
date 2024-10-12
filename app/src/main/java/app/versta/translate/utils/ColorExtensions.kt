package app.versta.translate.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

fun Color.darken(factor: Float): Color {
    val argb = this.toArgb()
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF

    val hsl = FloatArray(3)
    rgbToHsl(r, g, b, hsl)

    // Reduce the lightness by the given factor
    hsl[2] = (hsl[2] * (1 - factor)).coerceIn(0f, 1f)

    return hslToColor(a, hsl)
}

fun Color.lighten(factor: Float): Color {
    val argb = this.toArgb()
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF

    val hsl = FloatArray(3)
    rgbToHsl(r, g, b, hsl)

    // Increase the lightness by the given factor
    hsl[2] = (hsl[2] + (1 - hsl[2]) * factor).coerceIn(0f, 1f)

    return hslToColor(a, hsl)
}

fun Color.shift(hueOffset: Float = 0f, saturationFactor: Float = 1f, lightnessFactor: Float = 1f): Color {
    val argb = this.toArgb()
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF

    val hsl = FloatArray(3)
    rgbToHsl(r, g, b, hsl)

    // Shift the hue by the given offset
    hsl[0] = (hsl[0] + hueOffset) % 360f
    if (hsl[0] < 0) hsl[0] += 360f

    // Scale the saturation and lightness by the given factors
    hsl[1] = (hsl[1] * saturationFactor).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] * lightnessFactor).coerceIn(0f, 1f)

    return hslToColor(a, hsl)
}

private fun rgbToHsl(r: Int, g: Int, b: Int, outHsl: FloatArray) {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f

    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    var h = 0f
    var s = 0f
    val l = (max + min) / 2f

    if (max != min) {
        s = if (l < 0.5f) delta / (max + min) else delta / (2f - max - min)
        h = when (max) {
            rf -> (gf - bf) / delta + if (gf < bf) 6 else 0
            gf -> (bf - rf) / delta + 2
            bf -> (rf - gf) / delta + 4
            else -> h
        }
        h /= 6f
    }

    outHsl[0] = h * 360f
    outHsl[1] = s
    outHsl[2] = l
}

private fun hslToColor(alpha: Int, hsl: FloatArray): Color {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]

    val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - kotlin.math.abs(h / 60f % 2f - 1f))

    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = ((r + m) * 255f).roundToInt(),
        green = ((g + m) * 255f).roundToInt(),
        blue = ((b + m) * 255f).roundToInt(),
        alpha = alpha
    )
}