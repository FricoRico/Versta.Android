package app.versta.translate.core.model

import app.versta.translate.adapter.outbound.LoggingPreferenceMemoryRepository
import app.versta.translate.utils.FileLoggingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeWatcher : LogFileWatcher {
        var watching = false
        var stopped = false

        override fun startWatching() {
            watching = true
        }

        override fun stopWatching() {
            watching = false
            stopped = true
        }
    }

    private class WatcherCapture {
        val watcher = FakeWatcher()
        var directory: File? = null
        var onChanged: (() -> Unit)? = null

        val factory: (File, () -> Unit) -> LogFileWatcher = { dir, callback ->
            directory = dir
            onChanged = callback
            watcher
        }
    }

    private lateinit var capture: WatcherCapture

    @Before
    fun setUp() {
        // viewModelScope is backed by Dispatchers.Main even when the work is
        // launched onto Dispatchers.IO, so unit tests must install one.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        capture = WatcherCapture()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(directory: File?): LoggingViewModel =
        LoggingViewModel(
            directory = directory,
            loggingPreferenceRepository = LoggingPreferenceMemoryRepository(),
            watcherFactory = capture.factory,
        )

    private fun awaitLogs(
        viewModel: LoggingViewModel,
        timeoutMs: Long = 5_000,
        predicate: (String) -> Boolean,
    ) = runBlocking {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!predicate(viewModel.logs.value)) {
            if (System.nanoTime() > deadline) break
            delay(10)
        }
    }

    @Test
    fun init_existingFile_populatesLogsAtColdStart() {
        val directory = tempFolder.newFolder()
        val content = "2026-10-28 10:00:00 I/Tag: first\n2026-10-28 10:00:01 W/Tag: second\n"
        File(directory, FileLoggingTree.LOG_FILE_NAME).writeText(content)

        val viewModel = viewModel(directory)
        awaitLogs(viewModel) { it.isNotEmpty() }

        assertEquals(content, viewModel.logs.value)
        assertTrue(capture.watcher.watching)
        assertEquals(directory, capture.directory)
    }

    @Test
    fun init_missingFile_staysEmpty() {
        val directory = tempFolder.newFolder()

        val viewModel = viewModel(directory)

        runBlocking { delay(250) }
        assertEquals("", viewModel.logs.value)
    }

    @Test
    fun watcherEvent_fileCreatedAfterStart_refreshesLogs() {
        val directory = tempFolder.newFolder()

        val viewModel = viewModel(directory)
        runBlocking { delay(250) }
        assertEquals("", viewModel.logs.value)

        // Same-process path from NEU-228 D2: app.log only appears after the
        // diagnostics toggle is enabled, long after the screen was opened.
        val content = "2026-10-28 10:05:00 I/Tag: hello\n"
        File(directory, FileLoggingTree.LOG_FILE_NAME).writeText(content)
        capture.onChanged!!.invoke()

        awaitLogs(viewModel) { it.isNotEmpty() }
        assertEquals(content, viewModel.logs.value)
    }

    @Test
    fun clearLogs_deletesFileAndBackupAndClearsState() {
        val directory = tempFolder.newFolder()
        val file = File(directory, FileLoggingTree.LOG_FILE_NAME)
        val backup = File(directory, FileLoggingTree.BACKUP_FILE_NAME)
        file.writeText("current\n")
        backup.writeText("previous\n")

        val viewModel = viewModel(directory)
        awaitLogs(viewModel) { it.isNotEmpty() }

        viewModel.clearLogs()
        awaitLogs(viewModel) { it.isEmpty() }

        assertEquals("", viewModel.logs.value)
        assertFalse(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun init_fileLargerThanTailWindow_showsTailAlignedToLineBoundary() {
        val directory = tempFolder.newFolder()
        val line = "A".repeat(99) + "\n"
        val tailWindow = 512 * 1024
        val builder = StringBuilder()
        while (builder.length < tailWindow + 50_000) {
            builder.append(line)
        }
        File(directory, FileLoggingTree.LOG_FILE_NAME).writeText(builder.toString())

        val viewModel = viewModel(directory)
        awaitLogs(viewModel) { it.isNotEmpty() }

        val logs = viewModel.logs.value
        assertTrue(logs.length <= tailWindow)
        assertTrue("tail must start on a full line", logs.startsWith("A".repeat(99)))
        assertTrue(logs.endsWith("\n"))
    }
}
