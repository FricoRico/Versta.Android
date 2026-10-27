package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LanguageCodeTest {
    @Test
    fun normalizeLanguageCode_simplifiedChineseVariants() {
        listOf("zh-Hans", "zh-CN", "zh", "ZH-Hans", "zh-hans").forEach {
            assertEquals("zh", normalizeLanguageCode(it))
        }
    }

    @Test
    fun normalizeLanguageCode_traditionalChineseVariants() {
        listOf("zh-Hant", "zh-TW", "zh-HK", "zh-MO", "zh_hant").forEach {
            assertEquals("zh_hant", normalizeLanguageCode(it))
        }
    }

    @Test
    fun normalizeLanguageCode_stripsRegionSubtags() {
        assertEquals("en", normalizeLanguageCode("en-US"))
        assertEquals("en", normalizeLanguageCode("en-GB"))
        assertEquals("pt", normalizeLanguageCode("pt-BR"))
    }

    @Test
    fun normalizeLanguageCode_keepsDistinctCodes() {
        listOf("no", "nb", "nn", "hbs").forEach {
            assertEquals(it, normalizeLanguageCode(it))
        }
    }

    @Test
    fun normalizeLanguageCode_catalogMeetsRegistryCode() {
        assertEquals(normalizeLanguageCode("zh-Hans"), normalizeLanguageCode("zh"))
        assertNotEquals(normalizeLanguageCode("zh-Hant"), normalizeLanguageCode("zh"))
    }

    @Test
    fun localeForLanguageCode_preservesChineseScript() {
        assertEquals("zh", localeForLanguageCode("zh_hant").language)
        assertEquals("Hant", localeForLanguageCode("zh_hant").script)
        assertEquals("zh", localeForLanguageCode("zh-Hans").language)
        assertEquals("", localeForLanguageCode("zh-Hans").script)
        assertEquals("en", localeForLanguageCode("en").language)
    }
}
