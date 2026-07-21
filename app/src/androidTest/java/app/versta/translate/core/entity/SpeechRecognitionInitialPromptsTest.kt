package app.versta.translate.core.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionInitialPromptsTest {

    @Test
    fun forLanguage_en_returnsPrompt() {
        val prompt = SpeechRecognitionInitialPrompts.forLanguage("en")

        assertNotNull(prompt)
        assertFalse(prompt!!.isBlank())
    }

    @Test
    fun forLanguage_nl_returnsPrompt() {
        val prompt = SpeechRecognitionInitialPrompts.forLanguage("nl")

        assertNotNull(prompt)
        assertFalse(prompt!!.isBlank())
    }

    @Test
    fun forLanguage_null_returnsNull() {
        assertNull(SpeechRecognitionInitialPrompts.forLanguage(null))
    }

    @Test
    fun forLanguage_blank_returnsNull() {
        assertNull(SpeechRecognitionInitialPrompts.forLanguage(""))
        assertNull(SpeechRecognitionInitialPrompts.forLanguage("   "))
    }

    @Test
    fun forLanguage_unknownLanguage_returnsNull() {
        assertNull(SpeechRecognitionInitialPrompts.forLanguage("fr"))
        assertNull(SpeechRecognitionInitialPrompts.forLanguage("zz"))
    }

    @Test
    fun forLanguage_isCaseInsensitive() {
        assertNotNull(SpeechRecognitionInitialPrompts.forLanguage("EN"))
        assertNotNull(SpeechRecognitionInitialPrompts.forLanguage("NL"))
    }

    @Test
    fun forLanguage_en_promptsFormattingSignals() {
        val prompt = SpeechRecognitionInitialPrompts.forLanguage("en")!!

        assertTrue(prompt.contains("9:15"))
        assertTrue(prompt.contains("25 euros"))
        assertTrue(prompt.contains("museum's"))
        assertTrue(prompt.contains("hadn't"))
        assertTrue(prompt.contains("?"))
    }

    @Test
    fun forLanguage_nl_promptsFormattingSignals() {
        val prompt = SpeechRecognitionInitialPrompts.forLanguage("nl")!!

        assertTrue(prompt.contains("9:15"))
        assertTrue(prompt.contains("25 euro"))
        assertTrue(prompt.contains("?"))
    }
}
