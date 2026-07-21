package app.versta.translate.adapter.outbound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechContextStoreTest {

    private var nowMs: Long = 1_000L

    private fun store(ttlMs: Long = SpeechContextStore.DEFAULT_TTL_MS): SpeechContextStore {
        return SpeechContextStore(ttlMs = ttlMs, now = { nowMs })
    }

    private fun tokens(vararg values: Int) = intArrayOf(*values)

    @Test
    fun recordThenGet_returnsSameContext() {
        val store = store()
        store.record("en", tokens(1, 2, 3))

        val result = store.get("en")

        assertArrayEquals(tokens(1, 2, 3), result)
    }

    @Test
    fun get_unknownLanguage_returnsEmpty() {
        val store = store()

        assertEquals(0, store.get("en").size)
    }

    @Test
    fun get_nullOrBlankLanguage_returnsEmpty() {
        val store = store()

        assertTrue(store.get(null).isEmpty())
        assertTrue(store.get("").isEmpty())
        assertTrue(store.get("  ").isEmpty())
    }

    @Test
    fun record_blankLanguage_isNoOp() {
        val store = store()

        store.record("", tokens(1, 2))
        store.record("  ", tokens(3, 4))
        store.record(null, tokens(5, 6))

        assertTrue(store.get("en").isEmpty())
    }

    @Test
    fun record_nullOrEmptyTokenIds_isNoOp() {
        val store = store()

        store.record("en", null)
        assertTrue(store.get("en").isEmpty())

        store.record("en", IntArray(0))
        assertTrue(store.get("en").isEmpty())
    }

    @Test
    fun record_emptyTokenIds_keepsExistingContext() {
        val store = store()
        store.record("en", tokens(1, 2, 3))

        store.record("en", IntArray(0))

        assertArrayEquals(tokens(1, 2, 3), store.get("en"))
    }

    @Test
    fun record_overwritesStandingContext() {
        val store = store()
        store.record("en", tokens(1, 2))

        store.record("en", tokens(9, 8, 7))

        assertArrayEquals(tokens(9, 8, 7), store.get("en"))
    }

    @Test
    fun get_withinTtl_returnsContext() {
        val store = store(ttlMs = 90_000)
        store.record("en", tokens(1, 2))

        nowMs += 90_000

        assertArrayEquals(tokens(1, 2), store.get("en"))
    }

    @Test
    fun get_afterTtl_returnsEmpty() {
        val store = store(ttlMs = 90_000)
        store.record("en", tokens(1, 2))

        nowMs += 90_001

        assertTrue(store.get("en").isEmpty())
    }

    @Test
    fun get_afterTtl_evictsEntry() {
        val store = store(ttlMs = 90_000)
        store.record("en", tokens(1, 2))
        nowMs += 90_001
        store.get("en")

        // Re-checking does not resurrect the expired entry, and a second get
        // sees the same empty result (no shadowing by the evicted entry).
        nowMs += 1
        assertTrue(store.get("en").isEmpty())
    }

    @Test
    fun ttl_isPerLanguage() {
        val store = store(ttlMs = 10_000)
        store.record("en", tokens(1))
        store.record("nl", tokens(2))
        nowMs += 20_000

        assertTrue(store.get("en").isEmpty())
        assertTrue(store.get("nl").isEmpty())
    }

    @Test
    fun context_doesNotLeakAcrossLanguages() {
        val store = store()
        store.record("en", tokens(1, 2))

        assertTrue(store.get("nl").isEmpty())
    }

    @Test
    fun clear_removesAllContext() {
        val store = store()
        store.record("en", tokens(1))
        store.record("nl", tokens(2))

        store.clear()

        assertTrue(store.get("en").isEmpty())
        assertTrue(store.get("nl").isEmpty())
    }

    @Test
    fun get_doesNotResetTtlByItself() {
        val store = store(ttlMs = 10_000)
        store.record("en", tokens(1))

        nowMs += 5_000
        assertTrue(store.get("en").isNotEmpty())
        nowMs += 5_000
        assertTrue(store.get("en").isNotEmpty())
        nowMs += 1
        assertFalse(store.get("en").isNotEmpty())
    }
}
