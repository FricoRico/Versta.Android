// gl_renderer.cc — GLES2 live-preview renderer used by VerstaGlSurfaceView.
// The full frame (camera + overlay blits) is drawn here per camera frame so
// the presented pixels and the overlay pose describe the SAME image — that
// same-pass composite is what makes the overlay read as glued to the scene.
// All GL touches happen on the owning GL thread; the EGL context itself is
// created and made current on the Kotlin side before any entry point is hit.

#include "include/gl_renderer.h"

#include <jni.h>
#include <stdio.h>
#include <string.h>

#define LOG_TAG "VerstaGl"
#include "include/Log.h"

namespace glr {
namespace {

constexpr const char* VERT = R"glsl(
attribute vec2 aPos;
uniform mat3 uUv;
uniform float uClipFlipY;
varying vec2 vUv;
void main() {
    gl_Position = vec4(aPos.x, aPos.y * uClipFlipY, 0.0, 1.0);
    vec3 uv = uUv * vec3(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5, 1.0);
    vUv = uv.xy;
}
)glsl";

constexpr const char* FRAG_CAMERA = R"glsl(
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
void main() {
    gl_FragColor = texture2D(uTex, vUv);
}
)glsl";

// Overlay variant: the UV matrix carries the tracker homography's
// perspective row, so the varying keeps w and the fragment divides.
constexpr const char* VERT_OVERLAY = R"glsl(
attribute vec2 aPos;
uniform mat3 uUv;
uniform float uClipFlipY;
varying vec3 vUv;
void main() {
    gl_Position = vec4(aPos.x, aPos.y * uClipFlipY, 0.0, 1.0);
    vUv = uUv * vec3(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5, 1.0);
}
)glsl";

constexpr const char* FRAG_OVERLAY = R"glsl(
precision mediump float;
varying vec3 vUv;
uniform sampler2D uTex;
void main() {
    gl_FragColor = texture2D(uTex, vUv.xy / vUv.z);
}
)glsl";

const GLfloat QUAD[] = {
    -1.f, -1.f,
     1.f, -1.f,
    -1.f,  1.f,
     1.f,  1.f,
};

GLuint compileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char info[512];
        glGetShaderInfoLog(shader, sizeof(info), nullptr, info);
        LOGE("GL shader compile failed: %s", info);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

// Fullscreen draw of the (already bound) texture through the caller's UV
// matrix. clipFlipY folds the row-order flip the readback path needs:
// glReadPixels starts at framebuffer y=0 (the display's bottom row unless we
// rasterize upside down), so the FBO pass runs with clipFlipY=-1 to hand the
// tracker a top-row-first image.
void drawUvQuad(GlRendererHandle* h, const Quad& q, const float uv9[9],
                float clipFlipY) {
    glUseProgram(q.program);

    // Column-vector mat3: upload the row-major matrix transposed.
    GLfloat uvT[9];
    for (int r = 0; r < 3; ++r) {
        for (int c = 0; c < 3; ++c) uvT[c * 3 + r] = uv9[r * 3 + c];
    }
    glUniformMatrix3fv(q.uUv, 1, GL_FALSE, uvT);
    glUniform1f(q.uClipFlipY, clipFlipY);
    glUniform1i(q.uTex, 0);

    glBindBuffer(GL_ARRAY_BUFFER, h->vbo);
    glEnableVertexAttribArray(q.aPos);
    glVertexAttribPointer(q.aPos, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(q.aPos);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

// Links one program and fetches its quad locations; zeroed Quad on failure.
Quad buildQuad(const char* vertSrc, const char* fragSrc, const char* name) {
    Quad q;
    GLuint vert = compileShader(GL_VERTEX_SHADER, vertSrc);
    GLuint frag = compileShader(GL_FRAGMENT_SHADER, fragSrc);
    if (!vert || !frag) {
        if (vert) glDeleteShader(vert);
        if (frag) glDeleteShader(frag);
        return q;
    }
    q.program = glCreateProgram();
    glAttachShader(q.program, vert);
    glAttachShader(q.program, frag);
    glLinkProgram(q.program);
    glDeleteShader(vert);
    glDeleteShader(frag);

    GLint linked = 0;
    glGetProgramiv(q.program, GL_LINK_STATUS, &linked);
    if (!linked) {
        char info[512];
        glGetProgramInfoLog(q.program, sizeof(info), nullptr, info);
        LOGE("GL %s program link failed: %s", name, info);
        glDeleteProgram(q.program);
        q.program = 0;
        return q;
    }
    q.aPos = glGetAttribLocation(q.program, "aPos");
    q.uUv = glGetUniformLocation(q.program, "uUv");
    q.uTex = glGetUniformLocation(q.program, "uTex");
    q.uClipFlipY = glGetUniformLocation(q.program, "uClipFlipY");
    return q;
}

// Lazily (re)allocates the readback FBO + color texture at the target size.
bool ensureReadbackTarget(GlRendererHandle* h, int w, int hgt) {
    if (h->fbo && h->fboW == w && h->fboH == hgt) return true;

    if (h->fboTex) glDeleteTextures(1, &h->fboTex);
    if (h->fbo) glDeleteFramebuffers(1, &h->fbo);

    glGenTextures(1, &h->fboTex);
    glBindTexture(GL_TEXTURE_2D, h->fboTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, hgt, 0, GL_RGBA,
                 GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    glGenFramebuffers(1, &h->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, h->fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, h->fboTex, 0);
    const bool ok =
        glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (!ok) {
        LOGE("readback FBO %dx%d incomplete", w, hgt);
        glDeleteFramebuffers(1, &h->fbo);
        glDeleteTextures(1, &h->fboTex);
        h->fbo = 0;
        h->fboTex = 0;
        return false;
    }
    h->fboW = w;
    h->fboH = hgt;
    return true;
}

} // namespace

GlRendererHandle* createRenderer() {
    auto* h = new GlRendererHandle();
    h->camera = buildQuad(VERT, FRAG_CAMERA, "camera");
    h->overlay = buildQuad(VERT_OVERLAY, FRAG_OVERLAY, "overlay");
    if (!h->camera.program || !h->overlay.program) {
        destroyRenderer(h);
        return nullptr;
    }

    glGenBuffers(1, &h->vbo);
    glBindBuffer(GL_ARRAY_BUFFER, h->vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(QUAD), QUAD, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return h;
}

void destroyRenderer(GlRendererHandle* h) {
    if (!h) return;
    if (h->fbo) glDeleteFramebuffers(1, &h->fbo);
    if (h->fboTex) glDeleteTextures(1, &h->fboTex);
    if (h->overlayTex) glDeleteTextures(1, &h->overlayTex);
    if (h->vbo) glDeleteBuffers(1, &h->vbo);
    if (h->camera.program) glDeleteProgram(h->camera.program);
    if (h->overlay.program) glDeleteProgram(h->overlay.program);
    delete h;
}

bool renderCamera(GlRendererHandle* h, GLuint cameraTex, int surfaceW,
                  int surfaceH, const float uv9[9]) {
    if (!h || !h->camera.program || cameraTex == 0 || surfaceW <= 0 || surfaceH <= 0) {
        return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, surfaceW, surfaceH);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTex);
    drawUvQuad(h, h->camera, uv9, 1.f);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

    return glGetError() == GL_NO_ERROR;
}

bool setOverlay(GlRendererHandle* h, int w, int hgt, const uint8_t* rgba) {
    if (!h || w <= 0 || hgt <= 0 || !rgba) return false;

    glActiveTexture(GL_TEXTURE0);
    if (!h->overlayTex) {
        glGenTextures(1, &h->overlayTex);
        h->overlayW = 0;
        h->overlayH = 0;
    }
    glBindTexture(GL_TEXTURE_2D, h->overlayTex);
    if (h->overlayW != w || h->overlayH != hgt) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, hgt, 0, GL_RGBA,
                     GL_UNSIGNED_BYTE, rgba);
        h->overlayW = w;
        h->overlayH = hgt;
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, hgt, GL_RGBA,
                        GL_UNSIGNED_BYTE, rgba);
    }
    glBindTexture(GL_TEXTURE_2D, 0);
    return glGetError() == GL_NO_ERROR;
}

bool renderOverlay(GlRendererHandle* h, int surfaceW, int surfaceH,
                   const float uv9[9]) {
    if (!h || !h->overlay.program || !h->overlayTex || surfaceW <= 0 || surfaceH <= 0) {
        return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, surfaceW, surfaceH);

    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, h->overlayTex);
    drawUvQuad(h, h->overlay, uv9, 1.f);
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_BLEND);

    return glGetError() == GL_NO_ERROR;
}

bool readbackFrame(GlRendererHandle* h, GLuint cameraTex, int outW, int outH,
                   const float uv9[9], uint8_t* out) {
    if (!h || !h->camera.program || cameraTex == 0 || outW <= 0 || outH <= 0 || !out) {
        return false;
    }
    if (!ensureReadbackTarget(h, outW, outH)) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, h->fbo);
    glViewport(0, 0, outW, outH);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTex);
    drawUvQuad(h, h->camera, uv9, -1.f);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, outW, outH, GL_RGBA, GL_UNSIGNED_BYTE, out);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return glGetError() == GL_NO_ERROR;
}

} // namespace glr

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_construct(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(glr::createRenderer());
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_destroy(JNIEnv*, jobject, jlong handle) {
    glr::destroyRenderer(reinterpret_cast<glr::GlRendererHandle*>(handle));
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_renderCameraNative(
        JNIEnv* env, jobject, jlong handle, jint textureId, jint surfaceW,
        jint surfaceH, jfloatArray uvMatrix) {
    jfloat uv[9];
    env->GetFloatArrayRegion(uvMatrix, 0, 9, uv);
    return glr::renderCamera(reinterpret_cast<glr::GlRendererHandle*>(handle),
                             static_cast<GLuint>(textureId), surfaceW, surfaceH,
                             uv)
                   ? JNI_TRUE
                   : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_readbackRgbaNative(
        JNIEnv* env, jobject, jlong handle, jint textureId, jint outW,
        jint outH, jfloatArray uvMatrix, jobject out) {
    auto* outAddr =
        reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(out));
    if (!outAddr ||
        env->GetDirectBufferCapacity(out) < outW * outH * 4LL) {
        return JNI_FALSE;
    }
    jfloat uv[9];
    env->GetFloatArrayRegion(uvMatrix, 0, 9, uv);
    return glr::readbackFrame(reinterpret_cast<glr::GlRendererHandle*>(handle),
                              static_cast<GLuint>(textureId), outW, outH, uv,
                              outAddr)
                   ? JNI_TRUE
                   : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_setOverlayRgbaNative(
        JNIEnv* env, jobject, jlong handle, jint w, jint hgt, jbyteArray rgba) {
    jsize len = env->GetArrayLength(rgba);
    if (len < w * hgt * 4) return JNI_FALSE;
    jbyte* bytes = env->GetByteArrayElements(rgba, nullptr);
    if (!bytes) return JNI_FALSE;
    const bool ok = glr::setOverlay(
        reinterpret_cast<glr::GlRendererHandle*>(handle), w, hgt,
        reinterpret_cast<const uint8_t*>(bytes));
    env->ReleaseByteArrayElements(rgba, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_liverender_LiveGlRenderer_renderOverlayNative(
        JNIEnv* env, jobject, jlong handle, jint surfaceW, jint surfaceH,
        jfloatArray uvMatrix) {
    jfloat uv[9];
    env->GetFloatArrayRegion(uvMatrix, 0, 9, uv);
    return glr::renderOverlay(reinterpret_cast<glr::GlRendererHandle*>(handle),
                              surfaceW, surfaceH, uv)
                   ? JNI_TRUE
                   : JNI_FALSE;
}

} // extern "C"
