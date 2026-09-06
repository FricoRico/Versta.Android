package app.versta.translate.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live overlay's per-session presentation gate: a re-enabled session on a
 * warm translation cache would otherwise paint its first composite — already
 * carrying the full previous-scene translation — on the tracker's unstable
 * opening ticks, which reads as one frame of the old overlay flashing back.
 */
class AnchorWarmupGateTest {

    private val ticks = 4

    @Test
    fun freshGateWithholdsUntilAnchorHeldForAllWarmupTicks() {
        val gate = AnchorWarmupGate(ticks)

        repeat(ticks - 1) { assertFalse(gate.admit(anchored = true)) }
        assertTrue(gate.admit(anchored = true))
        assertTrue("gate stays open once admitted", gate.admit(anchored = true))
    }

    @Test
    fun anchorlessTicksPauseTheCountWithoutResettingIt() {
        val gate = AnchorWarmupGate(ticks)

        repeat(ticks - 1) { gate.admit(anchored = true) }
        assertFalse(gate.admit(anchored = false))
        assertFalse(gate.admit(anchored = false))
        assertTrue("the withheld count survives dropped anchors", gate.admit(anchored = true))
    }

    @Test
    fun anchorlessTicksAfterAdmissionKeepTheGateOpen() {
        val gate = AnchorWarmupGate(ticks)

        repeat(ticks) { gate.admit(anchored = true) }

        assertTrue(gate.admit(anchored = false))
    }

    @Test
    fun resetClosesTheGateForTheNextSession() {
        val gate = AnchorWarmupGate(ticks)
        repeat(ticks) { gate.admit(anchored = true) }

        gate.reset()

        assertFalse(gate.admit(anchored = true))
    }

    @Test
    fun gateNeverOpensOnAnAnchorlessSession() {
        val gate = AnchorWarmupGate(ticks)

        repeat(2 * ticks) { assertFalse(gate.admit(anchored = false)) }
    }
}
