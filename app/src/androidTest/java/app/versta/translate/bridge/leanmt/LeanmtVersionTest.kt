package app.versta.translate.bridge.leanmt

import org.junit.Assert.assertEquals
import org.junit.Test

// Real-driver test: loads the native library on-device and reads the vendored
// leanmt's kBinaryFileVersion (src/Io.hh). The value is pinned to the NEU-25
// contract: Versta.Models declares LEANMT_BINARY_VERSION = 1
// (translation/versta/leanmt.py) and ships it as leanmt_binary_version in
// bundle manifests. A bump is a coordinated change across leanmt, the model
// pipeline, and this app — updating this constant alone is never correct.
class LeanmtVersionTest {
    @Test
    fun binaryFileVersion_matchesPipelineContract() {
        assertEquals(1L, LeanmtVersion().binaryFileVersion())
    }
}
