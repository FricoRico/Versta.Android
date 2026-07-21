package app.versta.translate.core.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.TransliterationAdapter
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class TextTranslationViewModel(
    private val translationViewModel: TranslationViewModel,
    private val languageViewModel: LanguageViewModel
) : ViewModel() {
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _input = MutableStateFlow("")
    val input = _input

    private val _inputTransliteration = MutableStateFlow("")
    val inputTransliteration = _inputTransliteration

    private val _intermediate = MutableStateFlow("")
    val intermediate = _intermediate

    private val _translated = MutableStateFlow("")
    val translated = _translated

    private val _translateOnInput = MutableStateFlow(false)
    val translateOnInput = _translateOnInput

    private val _translatedTransliteration = MutableStateFlow("")
    val translatedTransliteration = _translatedTransliteration

    private val _languageModelFiles = languageViewModel.languageModelFiles.distinctUntilChanged()
    private val _languageOptions = languageViewModel.languageOptions.distinctUntilChanged()
    private val _languages = languageViewModel.languagePair.distinctUntilChanged()

    private var _inputTransliterator: TransliterationAdapter? = null
    private var _translationTransliterator: TransliterationAdapter? = null

    private val _translationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _transliterationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun translate(text: String) {
        _translationScope.launch {
            val source = _languageOptions.first()?.source

            val files = _languageModelFiles.first() ?: return@launch
            val languages = _languages.first() ?: return@launch

            clearTranslation()

            if (source is AutoDetectLanguage) {
                translationViewModel.load(files, languages)
            }

            val output = translationViewModel.translate(text, languages)
            setTranslation(output)
        }
    }

    private fun transliterateInput(text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        _transliterationScope.launch {
            _inputTransliteration.value = _inputTransliterator?.transliterate(text) ?: ""
        }
    }

    private fun transliterateTranslation(text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        _transliterationScope.launch {
            _translatedTransliteration.value = _translationTransliterator?.transliterate(text) ?: ""
        }
    }

    /**
     * Set the input.
     */
    fun setInput(text: String) {
        _input.value = text

        languageViewModel.setAutoDetectInput(text)
        transliterateInput(text)
    }

    /**
     * Clear the input.
     */
    fun clearInput() {
        _input.value = ""
        _inputTransliteration.value = ""
    }

    /**
     * Set the translation.
     */
    fun setIntermediate(text: String) {
        _intermediate.value = text
    }

    /**
     * Set the translation.
     */
    fun setTranslation(text: String) {
        _translated.value = text

        transliterateTranslation(text)
    }

    /**
     * Clear the translation.
     */
    fun clearTranslation() {
        _intermediate.value = ""

        _translated.value = ""
        _translatedTransliteration.value = ""
    }

    /**
     * Enable or disable automatic translation on input.
     */
    fun setTranslateOnInput(value: Boolean) {
        _translateOnInput.value = value
    }

    /**
     * Load the transliterator.
     */
    fun load(languages: LanguagePair) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = LoadingProgress.InProgress

            try {
                _translationTransliterator =
                    TransliterationAdapter(locale = languages.target.locale)

                _loadingProgress.value = LoadingProgress.Completed
            } catch (e: Exception) {
                e.printStackTrace()
                _loadingProgress.value = LoadingProgress.Error(e)
            }
        }
    }

    /**
     * Copy the translated text.
     */
    fun copyTranslatedText(context: Context) {
        viewModelScope.launch {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Translated text", translated.first())
            clipboard.setPrimaryClip(clip)
        }
    }

    /**
     * Share the translated text.
     */
    fun shareTranslatedText(context: Context) {
        viewModelScope.launch {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, translated.first())
            }

            val chooser = Intent.createChooser(shareIntent, "Share this translation")
            context.startActivity(chooser, null)
        }
    }

    /**
     * Reload the transliterator.
     */
    fun reload() {
        viewModelScope.launch {
            _languages.collect {
                if (it != null) {
                    load(it)
                }
            }
        }
    }

    init {
        reload()
    }

    companion object {
        private val TAG = TextTranslationViewModel::class.java.simpleName
    }
}