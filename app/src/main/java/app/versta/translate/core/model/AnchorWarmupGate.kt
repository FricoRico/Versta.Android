package app.versta.translate.core.model

/**
 * Gates the live overlay's first presentation per live-translate session: the
 * tracker's anchor wobbles for its first ticks, and a composite admitted that
 * early paints one frame of overlay on unsettled geometry (on warm MT that
 * frame already carries full translations, reading as "the previous session
 * flashed back"). Anchor-null ticks pause the count instead of resetting it,
 * so a flickery tracker strains through after enough anchored frames rather
 * than never gating open.
 *
 * Admitted on the GL thread, reset from session transitions on the main
 * thread; the counter is a best-effort cross-thread signal.
 */
class AnchorWarmupGate(private val ticks: Int = DEFAULT_WARMUP_TICKS) {

    @Volatile
    private var accepted = 0

    fun reset() {
        accepted = 0
    }

    /** @param anchored whether this frame's tracker pose exists.
     *  @return true once this session's overlay may composite. */
    fun admit(anchored: Boolean): Boolean {
        if (accepted >= ticks) return true
        if (anchored) accepted++
        return accepted >= ticks
    }

    companion object {
        /** Anchored ticks to wait out (~0.3 s at preview rate). */
        const val DEFAULT_WARMUP_TICKS = 8
    }
}
