package app.versta.translate.core.model

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPairModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TextTranslationViewModelTest {
    private class FakeTranslationLanguageProvider(
        modelFiles: PivotPairModelFiles? = null,
        pair: LanguagePair? =
            LanguagePair(
                source = Language.fromIsoCode("en"),
                target = Language.fromIsoCode("ja"),
            ),
        options: LanguageOptionPair? =
            LanguageOptionPair(
                source = Language.fromIsoCode("en"),
                target = Language.fromIsoCode("ja"),
            ),
    ) : TranslationLanguageProvider {
        override val languageOptions: Flow<LanguageOptionPair?> = MutableStateFlow(options)
        override val languagePair: Flow<LanguagePair?> = MutableStateFlow(pair)
        override val languageModelFiles: Flow<PivotPairModelFiles?> = MutableStateFlow(modelFiles)

        val autoDetectInputs = mutableListOf<String>()

        override fun setAutoDetectInput(text: String) {
            autoDetectInputs += text
        }
    }

    private class FakeTranslationRunner(
        private val output: String = "",
    ) : TranslationRunner {
        val translateCalls = mutableListOf<String>()
        val loadCalls = mutableListOf<PivotPairModelFiles?>()
        val errors = mutableListOf<Throwable>()

        override suspend fun load(
            files: PivotPairModelFiles?,
            languages: LanguagePair,
        ) {
            loadCalls += files
        }

        override suspend fun translate(
            input: String,
            languages: LanguagePair,
        ): String {
            translateCalls += input
            return output
        }

        override fun setTranslationError(throwable: Throwable) {
            errors += throwable
        }
    }

    private val model =
        LanguageModel(
            baseModel = "Helsinki-NLP/opus-mt-en-ja",
            path = Path(""),
            version = "v1.0.0",
            score = 52.8,
            files =
                LanguageModelFiles(
                    model = Path(""),
                    vocabulary = Path(""),
                    shortlist = Path(""),
                ),
            config =
                LanguageModelConfiguration(
                    encoderLayers = 6,
                    decoderLayers = 6,
                    ffnDepth = 2,
                    numHeads = 8,
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun awaitTranslationError(runner: FakeTranslationRunner): Throwable =
        runBlocking {
            withTimeout(5.seconds) {
                while (runner.errors.isEmpty()) {
                    delay(10)
                }
            }

            runner.errors.first()
        }

    @Test
    fun `translate without installed model surfaces typed error and never runs inference`() {
        val languages = FakeTranslationLanguageProvider(modelFiles = null)
        val runner = FakeTranslationRunner()
        val viewModel =
            TextTranslationViewModel(
                translationViewModel = runner,
                languageViewModel = languages,
            )

        viewModel.translate("Hello traveller")

        val error = awaitTranslationError(runner)

        assertTrue(error is LanguageModelUnavailableException)
        assertTrue(runner.translateCalls.isEmpty())
        assertTrue(runner.loadCalls.isEmpty())
        assertEquals("", viewModel.translated.value)
    }

    @Test
    fun `translate without installed model reports LoadingProgress Error on the view model`() {
        val languages = FakeTranslationLanguageProvider(modelFiles = null)
        val runner = FakeTranslationRunner()
        val viewModel =
            TextTranslationViewModel(
                translationViewModel = runner,
                languageViewModel = languages,
            )

        viewModel.translate("Hello traveller")

        val progress =
            runBlocking {
                withTimeout(5.seconds) {
                    viewModel.loadingProgress.filterIsInstance<LoadingProgress.Error>().first()
                }
            }

        assertTrue(progress.exception is LanguageModelUnavailableException)
    }

    @Test
    fun `translate with installed model runs inference and publishes the result`() {
        val files =
            PivotPairModelFiles(
                intermediary = null,
                output = model,
            )
        val languages = FakeTranslationLanguageProvider(modelFiles = files)
        val runner = FakeTranslationRunner(output = "こんにちは")
        val viewModel =
            TextTranslationViewModel(
                translationViewModel = runner,
                languageViewModel = languages,
            )

        viewModel.translate("Hello traveller")

        runBlocking {
            withTimeout(5.seconds) {
                while (runner.translateCalls.isEmpty()) {
                    delay(10)
                }
            }
        }

        assertEquals(listOf("Hello traveller"), runner.translateCalls)
        assertTrue(runner.errors.isEmpty())
        assertEquals("こんにちは", viewModel.translated.value)
    }
}
