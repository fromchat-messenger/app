plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "ru.fromchat.plugins.host"
        compileSdk = 37
        minSdk = 24
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":plugins:sdk"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        jvmMain.dependencies {
            implementation("net.bytebuddy:byte-buddy:1.15.11")
            implementation("net.bytebuddy:byte-buddy-agent:1.15.11")
        }
        androidMain.dependencies {
            implementation("top.canyie.pine:core:0.3.0")
        }
    }
}
