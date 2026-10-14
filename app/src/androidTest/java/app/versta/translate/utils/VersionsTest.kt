package app.versta.translate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun doubleDigitSegments_sortAfterSingleDigit() {
        assertTrue(compareVersions("v1.10.0", "v1.2.0") > 0)
    }

    @Test
    fun equalVersions_compareEqual() {
        assertEquals(0, compareVersions("v1.2.0", "v1.2.0"))
    }

    @Test
    fun missingPatchSegment_countsAsZero() {
        assertEquals(0, compareVersions("v1.2", "v1.2.0"))
    }

    @Test
    fun majorVersionDominatesMinorAndPatch() {
        assertTrue(compareVersions("v2.0.0", "v1.9.9") > 0)
        assertTrue(compareVersions("v1.9.9", "v2.0.0") < 0)
    }

    @Test
    fun leadingV_isIgnored() {
        assertEquals(0, compareVersions("1.2.0", "v1.2.0"))
    }

    @Test
    fun patchDifference_ordersNumerically() {
        assertTrue(compareVersions("v1.2.10", "v1.2.9") > 0)
    }

    @Test
    fun nonNumericSegment_fallsBackToLexicographic() {
        assertTrue(compareVersions("v1.x.0", "v1.2.0") > 0)
        assertTrue(compareVersions("v1.2.0", "v1.x.0") < 0)
    }
}
