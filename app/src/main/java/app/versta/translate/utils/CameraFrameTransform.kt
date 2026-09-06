package app.versta.translate.utils

/**
 * Camera-buffer → display UV math for the GL preview path.
 *
 * We sample the camera's external texture directly: [displayUvMatrix] maps
 * display-normalized coordinates (top-left origin, y down) to the buffer's
 * own normalized texel space, applying
 *
 *   1. an aspect-fill crop in display space (the viewfinder shows the whole
 *      narrower axis and crops the wider one), then
 *   2. the display→buffer rotation, the inverse of [rotationQuadrant], i.e.
 *      the quadrant that UNDOES the sensor mount rotation. CameraX writes the
 *      buffer in sensor orientation and its contract is that rotating the
 *      buffer's content by `rotationDegrees()` (sensor→display, CW) yields an
 *      upright image — so the sampling map must rotate back by quadrants
 *      `(4 − sensorQuadrant) % 4` (e.g. sensor quadrant 1 → quadrant 3).
 *
 * `SurfaceTexture.getTransformMatrix` is deliberately NOT applied: the
 * producer transform matrix represents the very same store-side rotation on
 * camera-service–produced surfaces (on the emulator it bakes in the 90°),
 * and chaining it would double-apply. This matches the known-good
 * implementations (CameraX-free OES consumers compute rotation+crop from
 * buffer/view geometry only).
 *
 * Returns a row-major 3×3 for homogeneous [u,v,1]. Column-vector convention:
 * out = M₂ × (M₁ × p).
 */
object CameraFrameTransform {

    fun displayUvMatrix(
        bufferW: Int,
        bufferH: Int,
        surfaceW: Int,
        surfaceH: Int,
        rotationQuadrant: Int,
    ): FloatArray {
        val crop = contentCropMatrix(bufferW, bufferH, surfaceW, surfaceH, rotationQuadrant)

        // Display-normalized (cropped) → buffer-normalized. Buffer content is
        // stored rotated q·90° CW from upright; the maps below are the inverse
        // lookups (display point → buffer texel).
        val rotate = when (((rotationQuadrant % 4) + 4) % 4) {
            // u = x,        v = y
            0 -> affine3x3(1f, 0f, 0f, 0f, 1f, 0f)
            // u = 1 - y,    v = x
            1 -> affine3x3(0f, -1f, 1f, 1f, 0f, 0f)
            // u = 1 - x,    v = 1 - y
            2 -> affine3x3(-1f, 0f, 1f, 0f, -1f, 1f)
            // u = y,        v = 1 - x
            else -> affine3x3(0f, 1f, 0f, -1f, 0f, 1f)
        }

        return multiply3(rotate, crop)
    }

    /**
     * Display-normalized → upright-frame-normalized coordinates: the
     * aspect-fill crop alone, WITHOUT the buffer rotation. The tracker and
     * the overlay bake live in the upright frame — not in the raw buffer's
     * rotated texel space — so overlays sampled through [overlayUvMatrix]
     * chain this, never [displayUvMatrix].
     */
    fun contentUvMatrix(
        bufferW: Int,
        bufferH: Int,
        surfaceW: Int,
        surfaceH: Int,
        rotationQuadrant: Int,
    ): FloatArray = contentCropMatrix(bufferW, bufferH, surfaceW, surfaceH, rotationQuadrant)

    /** Aspect-fill crop in display space (identity when dims are degenerate). */
    private fun contentCropMatrix(
        bufferW: Int,
        bufferH: Int,
        surfaceW: Int,
        surfaceH: Int,
        rotationQuadrant: Int,
    ): FloatArray {
        if (bufferW <= 0 || bufferH <= 0 || surfaceW <= 0 || surfaceH <= 0) {
            return IDENTITY_3X3.copyOf()
        }

        val q = ((rotationQuadrant % 4) + 4) % 4
        val displayAspectOfBuffer = if (q == 1 || q == 3) {
            bufferH.toFloat() / bufferW
        } else {
            bufferW.toFloat() / bufferH
        }
        val viewAspect = surfaceW.toFloat() / surfaceH

        return if (displayAspectOfBuffer > viewAspect) {
            val vis = viewAspect / displayAspectOfBuffer
            affine3x3(vis, 0f, (1f - vis) / 2f, 0f, 1f, 0f)
        } else {
            val vis = displayAspectOfBuffer / viewAspect
            affine3x3(1f, 0f, 0f, 0f, vis, (1f - vis) / 2f)
        }
    }

    /**
     * Overlay-texture UV matrix for one frame: display-normalized p → the
     * baked overlay's normalized texels. Starts from [contentUvMatrix] — the
     * UPRIGHT frame space the tracker and the bake share (never the rotated
     * buffer space of [displayUvMatrix]) — then into frame px by (fw, fh),
     * back to canonical px through H⁻¹, normalized by the bake dims.
     * Extra elements past the first 9 are ignored. Null when [homography]
     * is singular — skip the pass.
     * May carry a non-constant perspective row; the fragment shader must
     * divide (u,v) by w.
     */
    fun overlayUvMatrix(
        contentUv: FloatArray,
        frameW: Int,
        frameH: Int,
        homography: FloatArray,
        overlayW: Int,
        overlayH: Int,
    ): FloatArray? {
        if (homography.size < 9) return null
        if (frameW <= 0 || frameH <= 0 || overlayW <= 0 || overlayH <= 0) return null
        val inv = invert3(homography) ?: return null
        val toFrame = floatArrayOf(frameW.toFloat(), 0f, 0f, 0f, frameH.toFloat(), 0f, 0f, 0f, 1f)
        val toTexel = floatArrayOf(1f / overlayW, 0f, 0f, 0f, 1f / overlayH, 0f, 0f, 0f, 1f)
        return multiply3(toTexel, multiply3(inv, multiply3(toFrame, contentUv)))
    }

    /** Row-major 3×3 inverse; null when the matrix is singular. */
    fun invert3(m: FloatArray): FloatArray? {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h
        val B = c * h - b * i
        val C = b * f - c * e
        val det = a * A + d * B + g * C
        if (kotlin.math.abs(det) < 1e-12f) return null
        val s = 1f / det
        return floatArrayOf(
            A * s, B * s, C * s,
            (f * g - d * i) * s, (a * i - c * g) * s, (c * d - a * f) * s,
            (d * h - e * g) * s, (b * g - a * h) * s, (a * e - b * d) * s,
        )
    }

    private fun affine3x3(a: Float, b: Float, tx: Float, c: Float, d: Float, ty: Float) =
        floatArrayOf(a, b, tx, c, d, ty, 0f, 0f, 1f)

    /** Row-major 3×3 multiply: `a × b` (applies b first). */
    private fun multiply3(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (r in 0..2) for (col in 0..2) {
            var sum = 0f
            for (k in 0..2) sum += a[r * 3 + k] * b[k * 3 + col]
            out[r * 3 + col] = sum
        }
        return out
    }

    private val IDENTITY_3X3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
}
