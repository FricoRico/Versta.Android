package app.versta.translate.ui.screen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.versta.translate.MainActivity
import app.versta.translate.MainApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-gate smoke test for the "languages are no longer loaded" symptom
 * class (NEU-110, seen on 1.3.2-beta.1): the translation catalog baked at
 * build time (res/raw/versta_translation_models.json, gated at bake time by
 * :app:validateBakedCatalogs) must produce at least one row on the
 * Settings -> Languages screen.
 *
 * Uses the real MainActivity + MainApplication.module graph, so the
 * production wiring is covered end to end:
 *  - an unparsable/zero-valid catalog crashes the ExternalLanguageModelsFileRepository
 *    init coroutine and takes the test process down (failure);
 *  - a catalog that parses but renders no pairs trips the waitUntil timeout
 *    (failure);
 *  - at least one rendered language-pair row lets the test pass.
 *
 * Pair rows carry LANGUAGE_PAIR_ROW_TEST_TAG on their Modifier in
 * LanguageSettings.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun languageSettingsRendersAtLeastOneLanguagePair() {
        composeRule.activityRule.scenario.onActivity {
            MainApplication.module.navigationViewModel.navigate(Screens.LanguageSettings, Screens.TextTranslation)
        }

        // waitUntil doubles as the assertion: it throws once the timeout
        // expires with no language-pair row in the semantics tree.
        composeRule.waitUntil(timeoutMillis = CATALOG_RENDER_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithTag(LANGUAGE_PAIR_ROW_TEST_TAG)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val CATALOG_RENDER_TIMEOUT_MILLIS = 30_000L
    }
}
