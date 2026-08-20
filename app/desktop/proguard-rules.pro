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
