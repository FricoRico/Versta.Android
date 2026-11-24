package app.versta.translate.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.BuildConfig
import app.versta.translate.MainActivity
import app.versta.translate.MainApplication
import app.versta.translate.R
import app.versta.translate.utils.FileLoggingTree
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Release-gate regression test for NEU-228: the Application Logs screen
 * collapsed in release builds because the LazyColumn was scrolled as a whole
 * (unbounded item widths), which hid every row from the semantics tree, and
 * pre-existing app.log content never surfaced.
 *
 * Runs against the real MainActivity + MainApplication.module graph. Writes a
 * marker app.log BEFORE the activity is launched so the cold-start read in
 * LoggingViewModel.init is exercised exactly as on device.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationLogsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val markerLine = "NEU-228 instrumentation marker line"

    @Before
    fun seedLogFile() {
        // Cold-start setup: app.log must exist before the app process and
        // its lazy LoggingViewModel come up.
        File(context.getExternalFilesDir(null), FileLoggingTree.LOG_FILE_NAME)
            .writeText("2026-10-28 10:00:00 I/NEU-228: $markerLine\n")
    }

    @Test
    fun applicationLogs_displaysExistingLogContent() {
        navigateToApplicationLogs()

        // waitUntil doubles as the assertion for D2: it throws after the
        // timeout if the seeded app.log content never reaches the semantics
        // tree.
        composeRule.waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(markerLine, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val emptyLabel = context.getString(R.string.application_logs_empty)
        assert(
            composeRule
                .onAllNodesWithText(emptyLabel)
                .fetchSemanticsNodes()
                .isEmpty(),
        ) { "Empty state must not show while log content exists" }
    }

    @Test
    fun applicationLogs_longLinesKeepHorizontalScrollSemantics() {
        navigateToApplicationLogs()

        composeRule.waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(markerLine, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val node =
            composeRule
                .onAllNodesWithText(markerLine, substring = true)
                .fetchSemanticsNodes()
                .first()

        assert(
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null,
        ) { "Log lines must keep per-line horizontal scrolling (softWrap = false)" }
    }

    @Test
    fun applicationLogs_releaseDiagnosticsRowExposesToggleableSemantics() {
        // The toggle row is release-only by design (BuildConfig.DEBUG gate).
        if (BuildConfig.DEBUG) return

        navigateToApplicationLogs()

        val title = context.getString(R.string.application_logs_diagnostics_toggle_title)
        val description = context.getString(R.string.application_logs_diagnostics_toggle_description)

        composeRule.waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(title)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule
            .onAllNodesWithText(title)[0]
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText(description)[0]
            .assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) {
            toggleableState() != null
        }

        val switch = composeRule.onAllNodes(isToggleable())[0]
        switch.assertIsDisplayed()

        val initial = toggleableState()

        switch.performClick()
        composeRule.waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) {
            toggleableState() != initial
        }

        // Restore the previous state so repeated runs start deterministic.
        composeRule.onAllNodes(isToggleable())[0].performClick()
    }

    private fun toggleableState(): ToggleableState? =
        composeRule
            .onAllNodes(isToggleable())
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.ToggleableState)

    private fun navigateToApplicationLogs() {
        composeRule.activityRule.scenario.onActivity {
            MainApplication.module.navigationViewModel.navigate(
                Screens.ApplicationLogs,
                Screens.TextTranslation,
            )
        }
    }

    private companion object {
        const val RENDER_TIMEOUT_MILLIS = 10_000L
    }
}
