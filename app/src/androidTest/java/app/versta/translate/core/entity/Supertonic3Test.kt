package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Supertonic3Test {
    @Test
    fun languagesCoverThirtyOneLanguages() {
        assertEquals(31, Supertonic3.LANGUAGES.size)
    }

    @Test
    fun voicesMapEveryLanguageToBothGenders() {
        assertEquals(62, Supertonic3.VOICES.size)
        assertEquals(
            Supertonic3.LANGUAGES,
            Supertonic3.VOICES.map { it.language }.toSet(),
        )
        Supertonic3.VOICES.groupBy { it.language }.forEach { (_, voices) ->
            assertEquals(setOf(VoiceGender.Female, VoiceGender.Male), voices.map { it.gender }.toSet())
        }
    }

    @Test
    fun uncoveredLanguagesAreUnsupported() {
        listOf("ca", "fa", "he", "zh").forEach { code ->
            assertFalse(code, Supertonic3.supports(Language.fromIsoCode(code)))
        }
    }

    @Test
    fun coveredLanguagesAreSupported() {
        listOf("en", "ja", "pt", "fr", "hi").forEach { code ->
            assertTrue(code, Supertonic3.supports(Language.fromIsoCode(code)))
        }
    }

    @Test
    fun defaultStyleFollowsGender() {
        val language = Language.fromIsoCode("en")

        assertTrue(Supertonic3.defaultStyle(language, VoiceGender.Female).startsWith("F"))
        assertTrue(Supertonic3.defaultStyle(language, VoiceGender.Male).startsWith("M"))
    }

    @Test
    fun defaultStyleIsDeterministicAndSpreadsLanguages() {
        val styles = Supertonic3.LANGUAGES.map { Supertonic3.defaultStyle(Language.fromIsoCode(it), VoiceGender.Female) }

        assertEquals(styles, Supertonic3.LANGUAGES.map { Supertonic3.defaultStyle(Language.fromIsoCode(it), VoiceGender.Female) })
        assertEquals((1..5).map { "F$it" }.toSet(), styles.toSet())
    }

    @Test
    fun unsupportedLanguageStillResolvesAStyle() {
        // The mapping never crashes; availability is gated on supports().
        val style = Supertonic3.defaultStyle(Language.fromIsoCode("zh"), VoiceGender.Female)

        assertTrue(style.matches(Regex("F[1-5]")))
    }

    @Test
    fun styleFileNameNestsUnderVoiceStylesDirectory() {
        assertEquals("voice_styles/F3.json", Supertonic3.styleFileName("F3"))
        assertNotEquals(Supertonic3.styleFileName("F3"), Supertonic3.styleFileName("M3"))
    }
}
