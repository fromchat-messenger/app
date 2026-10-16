plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.google.services) apply false
}

/**
 * Single source of truth for app version (APK + generated [AppBuildInfo]).
 *
 * CI injects `-PversionName` / `-PversionCode` (exported as `ORG_GRADLE_PROJECT_*` by
 * .github/actions/set-version) instead of rewriting this file. This file is hashed into the
 * Gradle build-cache key, so a per-release `sed` here gave every release a brand-new cache key
 * and orphaned the previous one — roughly a gigabyte of dead cache per release tag.
 */
extra["versionName"] = providers.gradleProperty("versionName").getOrElse("1.2.0")
extra["versionCode"] = providers.gradleProperty("versionCode").getOrElse("120").toInt()

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.google.services)
    }
}