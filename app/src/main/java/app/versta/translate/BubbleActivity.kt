package app.versta.translate

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.versta.translate.MainApplication.Companion.TRANSLATION_NOTIFICATION_CHANNEL_ID
import app.versta.translate.adapter.inbound.TranslateBubbleNotification
import app.versta.translate.adapter.inbound.TranslateBubbleShortcut
import app.versta.translate.adapter.inbound.TranslateNotification
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelectionDrawer
import app.versta.translate.ui.component.TranslatorLoadingProgressDialog
import app.versta.translate.ui.screen.MinimalTextTranslation
import app.versta.translate.ui.theme.TranslateTheme
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BubbleActivity : ComponentActivity() {
    private val languageViewModel by viewModels<LanguageViewModel>(
        factoryProducer = {
            viewModelFactory {
                LanguageViewModel(
                    languageRepository = MainApplication.module.languageRepository,
                    languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
                )
            }
        }
    )

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                handleStartupAndResume(intent)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(updateReceiver, IntentFilter(TRANSLATION_NOTIFICATION_CHANNEL_ID), RECEIVER_EXPORTED)

        TranslateBubbleShortcut.registerForActivity(this)
        TranslateBubbleNotification.registerForActivity(this)

        handleStartupAndResume(intent)
        handleTargetLanguageUpdate(this)

        setContent {
            TranslateTheme {
                Surface (
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    MinimalTextTranslation(
                        languageViewModel = languageViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel,
                        translationViewModel = MainApplication.module.translationViewModel
                    )

                    TranslatorLoadingProgressDialog(
                        translationViewModel = MainApplication.module.translationViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel
                    )

                    LanguageSelectionDrawer(
                        languageViewModel = languageViewModel,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(updateReceiver)

        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleStartupAndResume(intent)
    }

    private fun handleStartupAndResume(intent: Intent) {
        val input = intent.getStringExtra("input")
        if (input != null) {
            MainApplication.module.textTranslationViewModel.setInput(input)
        }
    }

    private fun handleTargetLanguageUpdate(activity: Activity) {
        lifecycleScope.launch {
            languageViewModel.targetLanguage.conflate().filterNotNull().collect {
                val text = MainApplication.module.textTranslationViewModel.input.first()

                TranslateBubbleShortcut.updateShortcutIcon(activity, it)
                TranslateBubbleNotification.showNotification(activity, text)
            }
        }
    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }
}