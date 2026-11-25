package app.versta.translate.core.model

import app.versta.translate.adapter.outbound.AudioPlayer
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TextToSpeechViewModelTest {
    /**
     * Stands in for the native engine: every synthesize call blocks until the
     * test releases it, like a long native call that cancellation cannot
     * interrupt. Each chunk returns its token id so playback shows which
     * request produced it.
     */
    private class BlockingInference : TextToSpeechInference {
        val entered = Semaphore(0)
        val released = Semaphore(0)
        val loadGate = Semaphore(Int.MAX_VALUE)
        var loadFailure: Exception? = null
        var voiceFailure: Exception? = null
        val synthesized: MutableList<Long> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var closes = 0

        override val sampleRate = 44_100

        override fun synthesize(
            tokens: LongArray,
            speed: Float,
        ): FloatArray {
            synthesized += tokens.first()
            entered.release()
            check(released.tryAcquire(5, TimeUnit.SECONDS)) { "test never released the engine" }
            return floatArrayOf(tokens.first().toFloat())
        }

        override fun setVoice(path: Path?) {
            voiceFailure?.let { throw it }
        }

        override fun clearVoice() = Unit

        override fun load(
            files: VoiceModelInferenceFiles,
            threads: Int,
        ) {
            loadGate.acquire()
            loadGate.release()
            loadFailure?.let { throw it }
        }

        override fun close() {
            closes++
        }
    }

    private class RecordingPlayer : AudioPlayer {
        val played: MutableList<Float> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var stops = 0

        override fun play(
            audio: FloatArray,
            sampleRate: Int,
        ) {
            played += audio.toList()
        }

        override fun stop() {
            stops++
        }

        override fun release() = Unit
    }

    /** Request "a" is one chunk (token 1); request "b" is two (tokens 2 and 3). */
    private class ScriptedTokenizer : TextToSpeechTokenizer {
        override fun tokenize(
            text: String,
            language: Language,
            splitSentences: Boolean,
        ): List<LongArray> =
            when (text) {
                "a" -> listOf(longArrayOf(1))
                "b" -> listOf(longArrayOf(2), longArrayOf(3))
                else -> emptyList()
            }
    }

    private class FakePreferences : TextToSpeechPreferenceRepository {
        val enabled = MutableStateFlow(true)
        val gender = MutableStateFlow(VoiceGender.Female)

        override fun getTextToSpeechEnabled(): Flow<Boolean> = enabled

        override suspend fun setTextToSpeechEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }

        override fun getSpeed(): Flow<Float> = flowOf(1f)

        override suspend fun setSpeed(speed: Float) = Unit

        override fun getGender(): Flow<VoiceGender> = gender

        override suspend fun setGender(gender: VoiceGender) {
            this.gender.value = gender
        }

        override fun getThreadCount(): Flow<Int> = flowOf(2)

        override suspend fun setThreadCount(count: Int) = Unit
    }

    private class FakeLanguages : LanguagePreferenceRepository {
        override fun getSourceLanguage(): Flow<LanguageOption?> = flowOf(null)

        override suspend fun setSourceLanguage(language: LanguageOption) = Unit

        override fun getTargetLanguage(): Flow<Language?> = flowOf(Language.fromIsoCode("en"))

        override suspend fun setTargetLanguage(language: Language) = Unit

        override fun getPivotTranslation(): Flow<Boolean> = flowOf(true)

        override suspend fun setPivotTranslation(enabled: Boolean) = Unit

        override fun getLanguagePair(): Flow<LanguageOptionPair?> = flowOf(null)

        override suspend fun swapLanguages() = Unit

        override suspend fun clearSourceLanguage() = Unit

        override suspend fun clearTargetLanguage() = Unit

        override suspend fun clearLanguageSelectionForPair(languagePair: LanguagePair) = Unit
    }

    private val english = Language.fromIsoCode("en")
    private val inference = BlockingInference()
    private val player = RecordingPlayer()
    private val preferences = FakePreferences()

    private fun viewModel() =
        TextToSpeechViewModel(
            tokenizer = ScriptedTokenizer(),
            model = inference,
            audioPlayer = player,
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = preferences,
            languagePreferenceRepository = FakeLanguages(),
        )

    private suspend fun awaitCondition(condition: () -> Boolean) {
        while (!condition()) {
            delay(10)
        }
    }

    private fun awaitEngineCall() = assertTrue("engine was never called", inference.entered.tryAcquire(5, TimeUnit.SECONDS))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a new request after cancel plays only its own audio despite the old call returning late`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                val vm = viewModel()
                vm.textToSpeechReady.first { it }

                vm.synthesize("a", english)
                awaitEngineCall()

                vm.cancelSynthesis()
                vm.synthesize("b", english)
                awaitEngineCall()

                // The superseded call returns now, while the new request is running.
                inference.released.release(3)

                awaitCondition { player.played.size >= 2 && vm.speechProgressState.value == TextToSpeechSynthesisState.Idle }
                assertEquals(listOf(2f, 3f), player.played.toList())
                assertNull(vm.textToSpeechError.value)
            }
        }

    @Test
    fun `disabling text-to-speech mid-synthesis stops playback and drops the late chunk`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                val vm = viewModel()
                vm.textToSpeechReady.first { it }

                vm.synthesize("a", english)
                awaitEngineCall()
                val stopsBefore = player.stops
                val closesBefore = inference.closes

                preferences.enabled.value = false
                awaitCondition { inference.closes > closesBefore }
                inference.released.release()

                // Give the late result time to (not) reach the player.
                delay(200)
                assertTrue(player.played.isEmpty())
                assertTrue(player.stops > stopsBefore)
                assertEquals(TextToSpeechSynthesisState.Idle, vm.speechProgressState.value)
                assertFalse(vm.textToSpeechReady.first())
            }
        }

    @Test
    fun `speech is not ready and requests are ignored while the model is still loading`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                inference.loadGate.drainPermits()
                val vm = viewModel()

                assertTrue(vm.voiceAvailable.first())
                assertFalse(vm.textToSpeechReady.first())

                vm.synthesize("a", english)
                assertTrue(inference.synthesized.isEmpty())

                inference.loadGate.release()
                vm.textToSpeechReady.first { it }
            }
        }

    @Test
    fun `a failed load keeps speech unavailable and requests never reach the engine`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                inference.loadFailure = IllegalStateException("broken bundle")
                val vm = viewModel()

                vm.loadingProgress.first { it is LoadingProgress.Error }
                assertFalse(vm.textToSpeechReady.first())

                vm.synthesize("a", english)
                delay(100)
                assertTrue(inference.synthesized.isEmpty())
                assertTrue(player.played.isEmpty())
            }
        }

    @Test
    fun `a rejected voice keeps speech unavailable`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                inference.voiceFailure = IllegalArgumentException("bad style")
                val vm = viewModel()

                vm.loadingProgress.first { it is LoadingProgress.Completed }
                vm.textToSpeechError.first { it != null }
                assertFalse(vm.textToSpeechReady.first())
            }
        }

    @Test
    fun `changing the voice gender withdraws readiness until the new voice is bound`() =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                val vm = viewModel()
                vm.textToSpeechReady.first { it }

                inference.voiceFailure = IllegalArgumentException("bad style")
                preferences.gender.value = VoiceGender.Male

                vm.textToSpeechError.first { it != null }
                assertFalse(vm.textToSpeechReady.first())
            }
        }
}
