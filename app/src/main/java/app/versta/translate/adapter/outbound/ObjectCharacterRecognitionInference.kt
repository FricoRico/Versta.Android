package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import app.versta.translate.core.entity.OcrAnalysisResult
import java.nio.ByteBuffer

interface ObjectCharacterRecognitionInference {

    /**
     * The presenter's per-frame pose + content cursors. Line payload never
     * crosses JNI per frame (measured: it dominated the GL sink at 28–55 ms
     * on-device); content is pulled separately only when [contentVersion] or
     * [anchorEpoch] moves.
     */
    data class LiveTick(
        val homography: FloatArray, // 9 floats, canonical→current frame
        val anchorEpoch: Int,
        val contentVersion: Int,
    )

    /**
     * Loads all modules of the given bundle. Idempotent per bundle path.
     */
    fun load(bundle: ObjectCharacterRecognitionBundleWithFiles, threads: Int = 4)

    /**
     * Runs the full OCR pipeline over the frame: detection, orientation
     * resolution, script routing and recognition. The frame is the GL
     * preview's upright readback (tightly packed R,G,B,A, top row first),
     * so rotation is already applied on the GL side.
     *
     * @param forcedRecognizer Module directory of the recognizer to force for
     *        all lines; null routes each strip by script classification.
     */
    fun analyzeLive(input: ByteBuffer, width: Int, height: Int, forcedRecognizer: String? = null): OcrAnalysisResult

    /**
     * Carries the live anchor forward through this frame's motion. Null means
     * no anchor is held; [probeLive]+[analyzeLive] handle that path.
     */
    fun tickLive(input: ByteBuffer, width: Int, height: Int): LiveTick?

    /**
     * Marshals the live overlays' current CONTENT (lines/strips/text) from
     * the native cache. Meaningful only right after [tickLive] reported a new
     * epoch or content version — pulling more often re-sends identical lines.
     */
    fun pullLiveContent(width: Int, height: Int): OcrAnalysisResult?

    /**
     * Runs the stills pipeline (docaligner rectification + glyph-matte
     * typography) over one captured RGBA frame.
     */
    fun analyzeStill(input: ByteBuffer, width: Int, height: Int, rotationDegrees: Int, forcedRecognizer: String? = null): OcrAnalysisResult

    /**
     * Feeds the tracker's stillness gate with one anchorless frame (no
     * pipeline; ms-scale, never blocks on the engine). Anchorless frames owe
     * the gate continuous samples or the 200 ms quiet window can never
     * open — the throttled acquire worker alone cannot provide them.
     */
    fun probeLive(input: ByteBuffer, width: Int, height: Int)

    fun cancel()

    fun close()
}
