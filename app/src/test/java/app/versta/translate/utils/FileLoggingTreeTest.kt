package app.versta.translate.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import java.io.IOException

class FileLoggingTreeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun logFile(directory: File) = File(directory, FileLoggingTree.LOG_FILE_NAME)

    private fun backupFile(directory: File) = File(directory, FileLoggingTree.BACKUP_FILE_NAME)

    /**
     * Fails the test if the tree under test ever reports a failure back
     * through Timber — that path re-enters every planted tree and recurses
     * into a StackOverflowError when the disk is full or unwritable.
     */
    private class TimberFallbackProbe : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            if (tag == "FileLoggingTree") {
                fail("FileLoggingTree reported through Timber: $message")
            }
        }
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun shouldEnableFileLogging_debugBuildsAlwaysLogToFile() {
        assertTrue(FileLoggingTree.shouldEnableFileLogging(isDebugBuild = true, diagnosticsEnabled = false))
        assertTrue(FileLoggingTree.shouldEnableFileLogging(isDebugBuild = true, diagnosticsEnabled = true))
    }

    @Test
    fun shouldEnableFileLogging_releaseBuildsRequireDiagnosticsToggle() {
        assertFalse(FileLoggingTree.shouldEnableFileLogging(isDebugBuild = false, diagnosticsEnabled = false))
        assertTrue(FileLoggingTree.shouldEnableFileLogging(isDebugBuild = false, diagnosticsEnabled = true))
    }

    @Test
    fun log_appendsFormattedLinesToLogFile() {
        val directory = tempFolder.newFolder()
        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        Timber.tag("TestTag").i("hello world")
        Timber.tag("OtherTag").w("careful")

        tree.close()

        val content = logFile(directory).readText()
        assertTrue(content.contains("I/TestTag: hello world"))
        assertTrue(content.contains("W/OtherTag: careful"))
    }

    @Test
    fun log_doesNotTouchDiskUntilFirstMessage() {
        val directory = tempFolder.newFolder()
        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        assertFalse(logFile(directory).exists())

        tree.close()
    }

    @Test
    fun log_keepsFileWithinByteCapAcrossRotations() {
        val directory = tempFolder.newFolder()
        val cap = 1024L
        val tree = FileLoggingTree(directory, maxBytes = cap)
        Timber.plant(tree)

        repeat(100) {
            Timber.tag("TestTag").i("rotation line %03d", it + 1)
        }

        tree.close()

        val log = logFile(directory)
        val backup = backupFile(directory)

        assertTrue(log.exists())
        assertTrue(log.length() <= cap)
        assertTrue(backup.exists())
        assertTrue(backup.length() <= cap)
    }

    @Test
    fun log_keepsNewestLinesInCurrentGeneration() {
        val directory = tempFolder.newFolder()
        val tree = FileLoggingTree(directory, maxBytes = 1024L)
        Timber.plant(tree)

        repeat(100) {
            Timber.tag("TestTag").i("rotation line %03d", it + 1)
        }

        tree.close()

        val content = logFile(directory).readText()
        assertTrue(content.contains("rotation line 100"))
        assertFalse(content.contains("rotation line 001"))
    }

    @Test
    fun log_recoversWhenLogFileClearedExternally() {
        val directory = tempFolder.newFolder()
        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        Timber.tag("TestTag").i("before clear")

        assertTrue(logFile(directory).delete())

        Timber.tag("TestTag").i("after clear")

        tree.close()

        val content = logFile(directory).readText()
        assertFalse(content.contains("before clear"))
        assertTrue(content.contains("after clear"))
    }

    @Test
    fun log_unwritableTargetFailsSilentlyWithoutRecursion() {
        val directory = tempFolder.newFolder()
        // A directory at the log path makes writer construction throw IOException.
        assertTrue(logFile(directory).mkdir())

        Timber.plant(TimberFallbackProbe())
        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        repeat(10) {
            Timber.tag("TestTag").e(IOException("simulated"), "line %d", it)
        }

        // Completing this call sequence without StackOverflowError or a thrown
        // exception is the assertion; the path stays untouched on disk.
        assertTrue(logFile(directory).isDirectory)
    }

    @Test
    fun log_afterFailureStaysDisabledWithoutRetryStorms() {
        val directory = tempFolder.newFolder()
        assertTrue(logFile(directory).mkdir())

        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        Timber.tag("TestTag").i("fails")

        // Remove the obstruction: the tree must not silently resume writing.
        assertTrue(logFile(directory).delete())

        Timber.tag("TestTag").i("still quiet")

        tree.close()

        assertFalse(logFile(directory).exists())
    }

    @Test
    fun log_nullDirectoryIsInert() {
        val tree = FileLoggingTree(null)
        Timber.plant(tree)

        Timber.tag("TestTag").i("nowhere to go")

        tree.close()
    }

    @Test
    fun close_stopsWritingPermanently() {
        val directory = tempFolder.newFolder()
        val tree = FileLoggingTree(directory)
        Timber.plant(tree)

        Timber.tag("TestTag").i("before close")
        tree.close()
        Timber.tag("TestTag").i("after close")

        val content = logFile(directory).readText()
        assertEquals(1, content.lines().count { it.contains("TestTag") })
        assertTrue(content.contains("before close"))
        assertFalse(content.contains("after close"))
    }
}
