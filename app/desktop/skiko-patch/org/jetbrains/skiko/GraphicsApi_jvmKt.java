package org.jetbrains.skiko;

/**
 * Compiled at build time (Linux packaging only) to shadow the class inside skiko-awt.jar,
 * which is later on the jpackage classpath than this app's own desktop.jar.
 *
 * Why: JetBrains builds Skia for linux/arm64 with skia_gl_standard="gles"
 * (tools/skia_release/build.py), so the GL statement is GLES-only. The stock
 * checker therefore rejects some hardware drivers (notably Parallels virgl) that
 * render perfectly well once Mesa's reported GL version is capped
 * (MESA_GL_VERSION_OVERRIDE=3.0, set by the launcher for arm64). This drop-in
 * replacement keeps the check out of the way; the version cap in the launch
 * environment is what keeps Skia from emitting GLSL ES 3.20 into those contexts.
 *
 * Keep the signature in sync with skiko's GraphicsApi_jvm.kt; a mismatch fails
 * the JavaCompile on the skiko classpath.
 */
public final class GraphicsApi_jvmKt {
    private GraphicsApi_jvmKt() {
    }

    public static final boolean isVideoCardSupported(
        GraphicsApi api,
        OS os,
        String renderer
    ) {
        return true;
    }
}
