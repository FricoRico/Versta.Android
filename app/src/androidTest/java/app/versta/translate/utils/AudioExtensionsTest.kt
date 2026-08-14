package app.versta.translate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioExtensionsTest {

    private fun sine(frequency: Float, length: Int, sampleRate: Int = 16000, amplitude: Float = 1f): FloatArray {
        return FloatArray(length) { i -> (amplitude * sin(2.0 * PI * frequency * i / sampleRate)).toFloat() }
    }

    @Test
    fun fftMagnitudes_silence_returnsZero() {
        assertTrue(FloatArray(512).fftMagnitudes().all { it == 0f })
    }

    @Test
    fun fftMagnitudes_sine_peaksAtExpectedBin() {
        // 440 Hz at 16 kHz over a 512-point FFT lands at bin 440 * 512 / 16000 = 14.
        val magnitudes = sine(440f, 512).fftMagnitudes()

        val peakBin = magnitudes.indices.maxBy { magnitudes[it] }

        assertEquals(14, peakBin)
    }

    @Test
    fun fftMagnitudes_fullScaleSine_peakLandsNearQuarter() {
        // Hann window coherent gain is 0.5: peak magnitude = A * 0.5 / 2 = 0.25.
        val magnitudes = sine(440f, 512).fftMagnitudes()

        assertEquals(0.25f, magnitudes.max(), 0.02f)
    }

    @Test
    fun fftMagnitudes_rejectsNonPowerOfTwo() {
        try {
            FloatArray(100).fftMagnitudes()
            assertTrue("fftMagnitudes must reject non power-of-two lengths", false)
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun spectrumBands_silence_returnsAllZeros() {
        val bands = FloatArray(256).fftMagnitudes().spectrumBands(sampleRate = 16000)

        assertEquals(SPECTRUM_BAND_COUNT, bands.size)
        assertTrue(bands.all { it == 0f })
    }

    @Test
    fun spectrumBands_sine_peaksInVocalBandOnly() {
        // A 440 Hz fundamental lives in band 2 of 8 log-spaced bands over 80 Hz - 8 kHz.
        val bands = sine(440f, 512).fftMagnitudes().spectrumBands(sampleRate = 16000)

        val peakBand = bands.indices.maxBy { bands[it] }

        assertTrue("peak band $peakBand should be near band 2", peakBand in 2..3)
        assertTrue(bands[peakBand] > 0.5f)
        // A band far away (high end) stays silent despite tilt gain.
        assertTrue(bands[SPECTRUM_BAND_COUNT - 1] < 0.1f)
    }

    @Test
    fun spectrumBands_midLevelSine_gammaLiftsMidrange() {
        // Amplitude 0.1 sine: peak magnitude ~0.025 ≈ -32 dBFS → raw level 0.36,
        // gamma-lifted to ~0.49, tilt-boosted (band 2 of 8) to ~0.57.
        val bands = sine(440f, 512, amplitude = 0.1f).fftMagnitudes().spectrumBands(sampleRate = 16000)

        val peakBand = bands.indices.maxBy { bands[it] }

        assertEquals(0.57f, bands[peakBand], 0.07f)
    }

    @Test
    fun spectrumBands_equalTones_highBandOutweighsLowBand() {
        // Speech tilts roughly -6 dB/octave downward left; the per-band tilt
        // gain makes an equal-amplitude 3 kHz tone read higher than 300 Hz.
        val length = 2048
        val pcm = FloatArray(length) { i ->
            (0.25f * sin(2.0 * PI * 300.0 * i / 16000.0)).toFloat() +
                    (0.25f * sin(2.0 * PI * 3000.0 * i / 16000.0)).toFloat()
        }
        val bands = pcm.fftMagnitudes(length).spectrumBands(sampleRate = 16000)

        val middle = SPECTRUM_BAND_COUNT / 2
        val lowPeak = (0 until middle).maxBy { bands[it] }
        val highPeak = (middle until SPECTRUM_BAND_COUNT).maxBy { bands[it] }

        assertTrue(
            "high band $highPeak (${bands[highPeak]}) should outweigh low band $lowPeak (${bands[lowPeak]})",
            bands[highPeak] > bands[lowPeak] + 0.05f,
        )
    }

    @Test
    fun spectrumBands_quietSine_belowFloorMapsToZero() {
        // Amplitude 0.001 sine is about -72 dBFS with the Hann gain, beneath the -50 dB floor.
        val bands = sine(440f, 512, amplitude = 0.001f).fftMagnitudes().spectrumBands(sampleRate = 16000)

        assertTrue(bands.all { it == 0f })
    }
}
