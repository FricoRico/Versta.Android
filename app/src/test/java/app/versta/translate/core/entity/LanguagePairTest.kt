package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePairTest {
    @Test
    fun uniqueId_normalizesCatalogBcp47ToRegistryCode() {
        val catalog = LanguagePair.fromIsoCodes("en", "zh-Hans")

        assertEquals("en-zh", catalog.uniqueId())
    }

    @Test
    fun uniqueEquals_catalogBcp47PairMatchesBundleRegistryPair() {
        val catalog = LanguagePair.fromIsoCodes("en", "zh-Hans")
        val bundle = LanguagePair.fromIsoCodes("zh", "en")

        assertTrue(catalog.uniqueEquals(bundle))
        assertEquals(catalog.id, LanguagePair.fromIsoCodes("en", "zh").id)
    }

    @Test
    fun uniqueId_traditionalChineseStaysDistinctFromSimplified() {
        val traditional = LanguagePair.fromIsoCodes("en", "zh-Hant")
        val simplified = LanguagePair.fromIsoCodes("en", "zh")

        assertEquals("en-zh_hant", traditional.uniqueId())
        assertFalse(traditional.uniqueEquals(simplified))
        assertNotEquals(traditional.id, simplified.id)
    }

    @Test
    fun isValid_chineseScriptDistinction() {
        assertTrue(LanguagePair.fromIsoCodes("zh-Hans", "zh-Hant").isValid())
        assertFalse(LanguagePair.fromIsoCodes("zh-Hans", "zh").isValid())
    }

    @Test
    fun fromId_roundTripsNormalizedPair() {
        val pair = LanguagePair.fromIsoCodes("en", "zh-Hant")

        assertTrue(LanguagePair.fromId(pair.id).uniqueEquals(pair))
    }
}
