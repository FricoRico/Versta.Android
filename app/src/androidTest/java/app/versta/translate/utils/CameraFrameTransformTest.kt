package app.versta.translate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrameTransformTest {

    private fun apply(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = m[6] * x + m[7] * y + m[8]
        return Pair(
            (m[0] * x + m[1] * y + m[2]) / w,
            (m[3] * x + m[4] * y + m[5]) / w,
        )
    }

    @Test
    fun identityWhenAspectsMatchAndNoRotation() {
        val m = CameraFrameTransform.displayUvMatrix(1000, 500, 200, 100, 0)
        val (u, v) = apply(m, 0.25f, 0.75f)
        assertEquals(0.25f, u, 1e-5f)
        assertEquals(0.75f, v, 1e-5f)
    }

    @Test
    fun undoQuadrantForSensorPortraitIsThree() {
        // Back camera, sensor mounted 90° (CameraX quadrant 1): the consumer
        // must sample with the inverse rotation (quadrant 3). Emulator
        // geometry: 1280x960 buffer into a 996x1749 portrait view → the
        // wider rotated axis (buffer width → display height) is center-cropped
        // in v; sampling runs u = y, v = 1 - x(cropped).
        val m = CameraFrameTransform.displayUvMatrix(1280, 960, 996, 1749, 3)
        val vis = (996f / 1749f) / 0.75f
        val off = (1f - vis) / 2f

        val (u00, v00) = apply(m, 0f, 0f)
        assertEquals(0f, u00, 1e-3f)
        assertEquals(1f - off, v00, 1e-3f)

        val (u10, v10) = apply(m, 1f, 0f)
        assertEquals(0f, u10, 1e-3f)
        assertEquals(off, v10, 1e-3f)

        val (u01, v01) = apply(m, 0f, 1f)
        assertEquals(1f, u01, 1e-3f)
        assertEquals(1f - off, v01, 1e-3f)

        val (uc, vc) = apply(m, 0.5f, 0.5f)
        assertEquals(0.5f, uc, 1e-3f)
        assertEquals(0.5f, vc, 1e-3f)
    }

    @Test
    fun rotationPreservesCenter() {
        // Square buffer into square view with a quarter turn: pure rotation
        // about the center.
        val m = CameraFrameTransform.displayUvMatrix(1080, 1080, 1080, 1080, 1)
        val (uc, vc) = apply(m, 0.5f, 0.5f)
        assertEquals(0.5f, uc, 1e-5f)
        assertEquals(0.5f, vc, 1e-5f)
        // q=1 maps (u, v) = (1 - y, x).
        val (u0, v0) = apply(m, 0f, 1f)
        assertEquals(0f, u0, 1e-5f)
        assertEquals(0f, v0, 1e-5f)
    }

    @Test
    fun landscapeBufferLetterboxesInPortraitOnlyOnXAxis() {
        // No rotation, wide buffer into tall view: crop the horizontal axis.
        val m = CameraFrameTransform.displayUvMatrix(1920, 1080, 1080, 1920, 0)
        val vis = (1080f / 1920f) / (1920f / 1080f)
        val off = (1f - vis) / 2f
        val (u0, v0) = apply(m, 0f, 0f)
        assertEquals(off, u0, 1e-3f)
        assertEquals(0f, v0, 1e-3f)
        val (u1, v1) = apply(m, 1f, 1f)
        assertEquals(1f - off, u1, 1e-3f)
        assertEquals(1f, v1, 1e-3f)
    }

    @Test
    fun invert3RoundtripsOnProjectiveMatrix() {
        // A genuine homography (non-constant perspective row).
        val h = floatArrayOf(1.2f, 0.1f, 30f, -0.05f, 0.9f, -12f, 1e-3f, 2e-4f, 1f)
        val inv = CameraFrameTransform.invert3(h)!!
        val (x, y) = apply(inv, 400f, 550f)
        val (rx, ry) = apply(h, x, y)
        assertEquals(400f, rx, 1e-2f)
        assertEquals(550f, ry, 1e-2f)
    }

    @Test
    fun singularMatrixHasNoInverse() {
        assertEquals(null, CameraFrameTransform.invert3(FloatArray(9)))
    }

    @Test
    fun contentUvIsTheCropWithoutRotation() {
        val contentUv = CameraFrameTransform.contentUvMatrix(1280, 960, 996, 1749, 3)
        // Aspect-fill crop of the upright content: x visible band only.
        val vis = (996f / 1749f) / 0.75f
        val off = (1f - vis) / 2f
        val (u0, v0) = apply(contentUv, 0f, 0f)
        assertEquals(off, u0, 1e-3f)
        assertEquals(0f, v0, 1e-3f)
        val (u1, v1) = apply(contentUv, 1f, 1f)
        assertEquals(1f - off, u1, 1e-3f)
        assertEquals(1f, v1, 1e-3f)
    }

    @Test
    fun identityHomographyMakesOverlaySampleTheFrame() {
        val contentUv = CameraFrameTransform.contentUvMatrix(1280, 960, 996, 1749, 3)
        val h = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val o = CameraFrameTransform.overlayUvMatrix(contentUv, 720, 960, h, 720, 960)!!
        // Frame dims == bake dims with identity pose ⇒ identical mapping.
        val (e1, u1) = apply(contentUv, 0.3f, 0.7f)
        val (a1, v1) = apply(o, 0.3f, 0.7f)
        assertEquals(e1, a1, 1e-5f)
        assertEquals(u1, v1, 1e-5f)
    }

    @Test
    fun translationHomographyShiftsOverlaySample() {
        val contentUv = CameraFrameTransform.contentUvMatrix(1280, 960, 996, 1749, 3)
        val h = floatArrayOf(1f, 0f, 10f, 0f, 1f, 20f, 0f, 0f, 1f) // canonical→frame
        val o = CameraFrameTransform.overlayUvMatrix(contentUv, 720, 960, h, 720, 960)!!
        val (fu, fv) = apply(contentUv, 0.5f, 0.5f)
        val (ou, ov) = apply(o, 0.5f, 0.5f)
        // Frame point f = H·c ⇒ overlay samples c = f − (10, 20) px.
        assertEquals(fu - 10f / 720f, ou, 1e-5f)
        assertEquals(fv - 20f / 960f, ov, 1e-5f)
    }

    @Test
    fun overlayMatrixAcceptsEpochPackedHomography() {
        // liveCompose packs the anchor epoch as element 9; the matrix uses 9.
        val contentUv = CameraFrameTransform.contentUvMatrix(1280, 960, 996, 1749, 3)
        val h = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 7f)
        val o = CameraFrameTransform.overlayUvMatrix(contentUv, 720, 960, h, 720, 960)!!
        val (e, u) = apply(contentUv, 0.25f, 0.25f)
        val (a, v) = apply(o, 0.25f, 0.25f)
        assertEquals(e, a, 1e-5f)
        assertEquals(u, v, 1e-5f)
    }
}
