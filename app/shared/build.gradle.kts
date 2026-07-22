@file:Suppress("TaskMissingDescription")

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

abstract class GenerateAppBuildInfoTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val debugBuild: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outRoot = outputDirectory.get().asFile
        check(outRoot.invariantSeparatorsPath.contains("/generated/")) {
            "AppBuildInfo must be written under a generated/ directory, got: $outRoot"
        }
        outRoot.deleteRecursively()
        outRoot.resolve("ru/fromchat").apply { mkdirs() }.resolve("AppBuildInfo.kt").writeText(
            """
            |package ru.fromchat
            |
            |/** Injected by Gradle into generated sources (not under src/). */
            |object AppBuildInfo {
            |    const val version = "${versionName.get()}"
            |    const val versionCode = ${versionCode.get()}
            |    const val isDebug = ${debugBuild.get()}
            |}
            |
            """.trimMargin()
        )
    }
}

val generateAppBuildInfo = tasks.register<GenerateAppBuildInfoTask>("generateAppBuildInfo") {
    versionName.set(rootProject.extra["versionName"] as String)
    versionCode.set(rootProject.extra["versionCode"] as Int)
    debugBuild.set(
        gradle.startParameter.taskNames.let { names ->
            !names.any { it.contains("Release", ignoreCase = true) } ||
                names.any { it.contains("Debug", ignoreCase = true) }
        },
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/appBuildInfo/kotlin"))
}

kotlin {
    android {
        namespace = "ru.fromchat.shared"
        minSdk = 24
        compileSdk = 37
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts("-framework", "UIKit")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        commonMain {
            kotlin.srcDir(generateAppBuildInfo.map { it.outputDirectory })
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.constraintlayout)
            implementation(libs.navigation.compose)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.compose)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.kotlinx.io.core)

            // Ktor - force version 2.3.12 to avoid conflicts with Coil 3's Ktor 3
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)

            // Datetime
            implementation(libs.kotlinx.datetime)

            // Coil for image loading (multiplatform)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)

            // SQLDelight runtime
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            implementation(project(":utils:shared"))
            implementation(libs.krypto)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }

        androidMain.dependencies {
            implementation(libs.markdown.renderer.m3)
            implementation(libs.bouncycastle.bcprov)
            implementation(libs.androidx.exifinterface)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.multiplatform.crypto.libsodium.bindings)
            implementation(libs.tweetnacl.java)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.livekit.android)
            implementation(libs.livekit.android.compose.components)
            implementation(libs.androidx.webkit)
        }

        iosMain.dependencies {
            implementation(libs.jetbrains.kotlinx.io.bytestring)
            implementation(libs.jetbrains.kotlinx.coroutines.core)
            implementation(libs.ktor.client.darwin)
            implementation(libs.multiplatform.crypto.libsodium.bindings)
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("MessageDatabase") {
            packageName.set("ru.fromchat.db")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ru.fromchat"
    generateResClass = auto
}

tasks.matching { it.name == "compileAndroidMain" || it.name == "compileKotlinIosArm64" }.configureEach {
    dependsOn("generateResourceAccessorsForCommonMain")
    dependsOn(generateAppBuildInfo)
}

tasks.register("generateResourceAccessors") {
    dependsOn(
        *(
            tasks.filter {
                it.name.startsWith("generateResourceAccessors") &&
                !it.name.matches("^(:${project.name})?generateResourceAccessors$".toRegex())
            }.toTypedArray()
        )
    )
}
