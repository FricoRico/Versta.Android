package app.versta.translate.core.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TransliterationAdapter
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TextTranslationViewModel(
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input

    private val _inputTransliteration = MutableStateFlow("")
    val inputTransliteration = _inputTransliteration

    private val _translated = MutableStateFlow("")
    val translated = _translated

    private val _translateOnInput = MutableStateFlow(false)
    val translateOnInput = _translateOnInput

    private val _translatedTransliteration = MutableStateFlow("")
    val translatedTransliteration = _translatedTransliteration

    private val languages = languagePreferenceRepository.getLanguagePair().distinctUntilChanged()

    private var _inputTransliterator: TransliterationAdapter? = null
    private var _translationTransliterator: TransliterationAdapter? = null

    private val _transliterationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Set the input.
     */
    fun setInput(text: String) {
        _input.value = text

        _transliterationScope.launch {
            _inputTransliteration.value = _inputTransliterator?.transliterate(text) ?: ""
        }
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
    fun setTranslation(text: String) {
        _translated.value = text

        _transliterationScope.launch {
            _translatedTransliteration.value = _translationTransliterator?.transliterate(text) ?: ""
        }
    }

    /**
     * Clear the translation.
     */
    fun clearTranslation() {
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
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = LoadingProgress.InProgress

            try {
                _inputTransliterator = TransliterationAdapter(locale = languages.source.locale)
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
            languages.collect {
                if (it != null) {
                    load(it)
                }
            }
        }
    }

    init {
        reload()
    }
}