@file:Suppress("UnstableApiUsage")

include(":utils:android")


rootProject.name = "FromChat"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Fallback when repo.maven.apache.org returns 403 on GitHub-hosted runners.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Fallback when repo.maven.apache.org returns 403 on GitHub-hosted runners.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        // LiveKit Android pulls com.github.davidliu:audioswitch from JitPack.
        maven("https://jitpack.io")
    }
}

include(":app:shared", ":utils:shared", ":app:android", ":app:desktop")
 