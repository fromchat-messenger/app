# Release shrinking: obfuscate/rename classes and members as aggressively as R8 allows.
# proguard-android-optimize.txt (from build.gradle) supplies repackageclasses, overloadaggressively, etc.

# ru.fromchat: keep names for plugin hooks; allow unused code removal.
-keepnames,allowshrinking class ru.fromchat.** { *; }
-keepclassmembernames,allowshrinking class ru.fromchat.** { *; }

# Plugin SDK loaded from plugin artifacts.
-keepnames class ru.fromchat.plugins.** { *; }

# Crash reports: keep real .kt file names and line numbers; class names stay obfuscated.
-keepattributes SourceFile,LineNumberTable

# Kotlin serialization — keep structure required at runtime; names may still be shortened.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers,allowobfuscation,allowshrinking class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,allowobfuscation,allowshrinking,includedescriptorclasses class **$$serializer {
    *;
}

-keepclasseswithmembers,allowobfuscation,allowshrinking class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLDelight generated adapters — referenced directly, safe to obfuscate names.
-keep,allowobfuscation,allowshrinking class ru.fromchat.db.** {
    *;
}

# Reflection-heavy runtime deps (strip unused code; suppress benign missing-class noise).
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn io.livekit.**
