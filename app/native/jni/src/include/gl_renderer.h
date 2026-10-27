#pragma once

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <stdint.h>

namespace glr {

/// One draw pipeline: program + its quad attribute/uniform locations; the
/// shared fullscreen VBO lives on the handle.
struct Quad {
    GLuint program = 0;
    GLint aPos = -1;
    GLint uUv = -1;
    GLint uTex = -1;
    GLint uClipFlipY = -1;
};

/**
 * GL live-preview renderer state. All entry points must run on the thread
 * that owns the current EGL context (the VerstaGlSurfaceView GL loop).
 */
struct GlRendererHandle {
    Quad camera;
    Quad overlay;      // projective-UV variant for the baked content texture
    GLuint vbo = 0;
    GLuint fbo = 0;
    GLuint fboTex = 0;
    int fboW = 0;
    int fboH = 0;
    GLuint overlayTex = 0;
    int overlayW = 0;
    int overlayH = 0;

};

GlRendererHandle* createRenderer();
void destroyRenderer(GlRendererHandle* h);

/// Draws the camera's external-OES texture fullscreen with the caller's
/// display→buffer UV matrix. Matrix is row-major 3×3 over [u,v,1] in display
/// top-left origin space (see CameraFrameTransform.kt).
bool renderCamera(GlRendererHandle* h, GLuint cameraTex, int surfaceW,
                  int surfaceH, const float uv9[9]);

/// (Re)uploads the baked overlay content: premultiplied memory-order R,G,B,A
/// texels of the full canonical frame (see OcrOverlayBaker.kt). Replaces any
/// previous bake. Sizing change reallocates; same-size bakes sub-image.
bool setOverlay(GlRendererHandle* h, int w, int hgt, const uint8_t* rgba);

/// Draws the current overlay texture fullscreen, sampled through the
/// frame's overlay UV matrix (CameraFrameTransform.overlayUvMatrix) — the
/// matrix is genuinely projective (carries the tracker homography's
/// perspective row), so the overlay fragment shader divides by w. Premultiplied
/// blend ONE, ONE_MINUS_SRC_ALPHA. No-op false without an uploaded bake.
bool renderOverlay(GlRendererHandle* h, int surfaceW, int surfaceH,
                   const float uv9[9]);

/// Renders the camera texture into an offscreen RGBA target of outW×outH and
/// reads the pixels into `out` (outW*outH*4 bytes, memory-order R,G,B,A,
/// first row = display top). Pass an aspect-matched uv so the readback holds
/// the full upright frame (no display crop) — that is the tracker's analysis
/// image. Leaves the default framebuffer bound on return.
bool readbackFrame(GlRendererHandle* h, GLuint cameraTex, int outW, int outH,
                   const float uv9[9], uint8_t* out);

} // namespace glr
