# ProGuard specializes return types of @JvmMultifileClass actuals (VerifyError: Paragraph vs SkiaParagraph).
# Upstream: https://github.com/JetBrains/compose-multiplatform/pull/5652 (CMP-10488)
-keep,allowshrinking,allowobfuscation class **Kt__* { *; }

# Ktor ContentNegotiation JSON extension (ServiceLoader).
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }

# Ktor CIO client engine (ServiceLoader).
-keep class io.ktor.client.engine.cio.CIOEngineContainer { *; }

# SQLite JDBC driver + JNI natives (ProGuard drops native methods otherwise).
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.sqlite.** { *; }

# JNA resolves some Java methods *from native code* by name (jnidispatch looks up
# com.sun.jna.Native#dispose via GetStaticMethodID during initIDs), and the app's
# Win32 mappings / WindowProc callbacks / Structures are loaded reflectively.
# ProGuard must not strip or rename any of it — stripping Native#dispose made every
# Windows package die at JVM start with "Failed to launch JVM"
# (UnsatisfiedLinkError: Can't obtain static method dispose from class com.sun.jna.Native,
# thrown from com.sun.jna.platform.win32.Shell32.<clinit> in Main.kt; Linux/macOS never
# load Shell32, which is why only the Windows builds were affected).
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# BouncyCastle security providers (ServiceLoader java.security.Provider).
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider { *; }

# Cryptography provider (ServiceLoader).
-keep class dev.whyoleg.cryptography.providers.jdk.JdkCryptographyProviderContainer { *; }

# Coil fetchers/decoders registered via ServiceLoader (network + SVG).
-keep class coil3.network.ktor3.internal.KtorNetworkFetcherServiceLoaderTarget { *; }
-keep class coil3.svg.internal.SvgDecoderServiceLoaderTarget { *; }

# Compose Multiplatform generated resource accessors (logo, strings, fonts).
-keep class ru.fromchat.Drawable*_commonMainKt { *; }
-keep class ru.fromchat.String*_commonMainKt { *; }
-keep class ru.fromchat.Font*_commonMainKt { *; }
-keep class ru.fromchat.ActualResourceCollectorsKt { *; }
-keep class ru.fromchat.Res { *; }
-keep class ru.fromchat.Res$* { *; }

# App JSON models.
-keep @kotlinx.serialization.Serializable class ru.fromchat.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ru.fromchat.** {
    <fields>;
    <init>(...);
}

# JetBrains Runtime bundles a `jogl.all.jmod` (JOGL, incl. its JavaFX bridge) in its jmods
# directory, which ProGuard picks up as a library jar. Those JOGL classes extend JavaFX
# classes that are ProGuard *program* inputs, so ProGuard reports 7x
# "library class com.jogamp.newt.javafx.* extends or implements program class javafx.*"
# and then aborts with "Please correct the above warnings first". The app never uses JOGL,
# so only that JavaFX bridge is ignored (JDKs without the jmod, e.g. Temurin, are unaffected).
-dontwarn com.jogamp.newt.javafx.**
