plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "ru.fromchat.shared"
        minSdk = 24
        compileSdk = 36
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
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

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime.ktx)
            implementation("org.purejava:tweetnacl-java:1.1.3")
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.constraintlayout)
            implementation(libs.navigation.compose)
            implementation(libs.compose.materialIconsExtended)
            implementation("androidx.compose.animation:animation:1.8.4")
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.androidx.core.ktx)

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

            implementation(project(":utils:shared"))
            implementation(libs.krypto)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }

        iosMain.dependencies {
            implementation(libs.jetbrains.kotlinx.io.bytestring)
            implementation(libs.jetbrains.kotlinx.coroutines.core)
            implementation(libs.ktor.client.darwin)
            implementation("com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings:0.9.5")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ru.fromchat"
    generateResClass = auto
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