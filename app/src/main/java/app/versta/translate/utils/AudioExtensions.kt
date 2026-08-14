package app.versta.translate.utils

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Number of spectrum bands the waveform visualizer works with. */
const val SPECTRUM_BAND_COUNT = 12

// Vocal range: speech fundamentals start ~85 Hz; formants reach ~8 kHz, which
// coincides with the Nyquist frequency of the 16 kHz capture rate, so the
// whole usable spectrum is covered.
private const val VOCAL_LOW_HZ = 40f

private const val LEVEL_FLOOR_DB = -50f
private const val MIN_MAGNITUDE = 1e-9f

// Perceptual loudness: speech band peaks sit far below full scale, so raw dB
// mapping leaves the visualization flat. A gamma < 1 lifts the midrange (a
// 0.3 band reads as ~0.43) while silence and full scale stay pinned.
private const val SPECTRUM_GAMMA = 0.7f

// Speech follows roughly a -6 dB/octave spectral tilt, so energy piles into
// the low end and the ridge leans left. Multiplicative per-band gain rising
// linearly across the log-spaced bands compensates. Multiplied AFTER the
// floor mapping so silence stays at exactly 0 everywhere.
private const val SPECTRUM_TILT_GAIN = 0.6f

/**
 * Computes the magnitude spectrum of the first [length] samples (must be a
 * power of two and <= size) using a Hann window and an iterative radix-2
 * Cooley-Tukey FFT. Returns one magnitude per frequency bin, bins 0..[length]/2,
 * normalized by window length so a full-scale sine lands near 0.25.
 */
fun FloatArray.fftMagnitudes(length: Int = size): FloatArray {
    require(length > 0 && length and (length - 1) == 0) { "length must be a power of two" }
    require(length <= size) { "length exceeds array size" }

    val re = FloatArray(length)
    val im = FloatArray(length)
    for (i in 0 until length) {
        re[i] = this[i] * (0.5f - 0.5f * cos(2f * Math.PI.toFloat() * i / (length - 1)))
    }

    val log2n = Integer.numberOfTrailingZeros(length)
    for (i in 0 until length) {
        val j = Integer.reverse(i) ushr (32 - log2n)
        if (j > i) {
            val tmpRe = re[i]; re[i] = re[j]; re[j] = tmpRe
            val tmpIm = im[i]; im[i] = im[j]; im[j] = tmpIm
        }
    }

    var span = 2
    while (span <= length) {
        val half = span / 2
        val step = -2f * Math.PI.toFloat() / span
        for (i in 0 until length step span) {
            for (j in 0 until half) {
                val angle = step * j
                val c = cos(angle)
                val s = sin(angle)
                val k = i + j + half
                val l = i + j
                val tre = re[k] * c - im[k] * s
                val tim = re[k] * s + im[k] * c
                re[k] = re[l] - tre
                im[k] = im[l] - tim
                re[l] += tre
                im[l] += tim
            }
        }
        span *= 2
    }

    return FloatArray(length / 2) { i ->
        sqrt(re[i] * re[i] + im[i] * im[i]) / length
    }
}

/**
 * Folds a magnitude spectrum (as returned by [fftMagnitudes]) into [bandCount]
 * log-spaced bands covering the vocal range ([VOCAL_LOW_HZ] Hz to Nyquist),
 * each band's peak magnitude mapped from dBFS to 0..1 with [LEVEL_FLOOR_DB]
 * treated as silence.
 */
fun FloatArray.spectrumBands(bandCount: Int = SPECTRUM_BAND_COUNT, sampleRate: Int): FloatArray {
    val fftLength = size * 2
    val low = ln(VOCAL_LOW_HZ)
    val high = ln(sampleRate / 2f)

    return FloatArray(bandCount) { band ->
        val f0 = exp(low + (high - low) * band / bandCount)
        val f1 = exp(low + (high - low) * (band + 1) / bandCount)

        var bin = maxOf(1, (f0 * fftLength / sampleRate).toInt())
        val end = minOf(size - 1, ceil(f1 * fftLength / sampleRate).toInt())
        var peak = 0f
        while (bin <= end) {
            if (this[bin] > peak) {
                peak = this[bin]
            }
            bin++
        }

        val db = 20f * log10(peak + MIN_MAGNITUDE)
        val level = ((db - LEVEL_FLOOR_DB) / -LEVEL_FLOOR_DB).coerceIn(0f, 1f)
        val tilt = 1f + SPECTRUM_TILT_GAIN * band / (bandCount - 1)
        (level.pow(SPECTRUM_GAMMA) * tilt).coerceIn(0f, 1f)
    }
}
