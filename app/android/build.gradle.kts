
import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.google.services)
}

abstract class FixComposeResTask : DefaultTask() {
    @get:InputFiles abstract val inputFiles: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        val outDir = outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        inputFiles.forEach { file ->
            if (file.exists()) {
                file.copyRecursively(
                    File(outDir, "composeResources/ru.fromchat"),
                    overwrite = true
                )
            }
        }
    }
}

val fixComposeResourcesStructure = tasks.register<FixComposeResTask>("fixComposeResourcesStructure") {
    val sharedProject = rootProject.project(":app:shared")

    inputFiles.from(
        sharedProject
            .layout
            .buildDirectory
            .dir(
                "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"
            )
    )

    outputDirectory.set(
        layout.buildDirectory.dir("intermediates/fixed_compose_res")
    )

    dependsOn(
        sharedProject.tasks.matching {
            it.name.contains("prepareComposeResources", ignoreCase = true)
        }
    )
}

extensions.configure<ApplicationExtension> {
    namespace = "ru.fromchat"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.fromchat"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        val keystoreProperties = Properties().apply {
            load(file("keys/keystore.properties").inputStream())
        }

        create("release") {
            storeFile = file("keys/release.jks")
            keyAlias = "key0"
            storePassword = keystoreProperties["releaseStorePassword"].toString()
            keyPassword = keystoreProperties["releaseKeyPassword"].toString()
            enableV3Signing = true
        }

        getByName("debug") {
            storeFile = file("keys/debug.jks")
            keyAlias = "debug"
            storePassword = keystoreProperties["debugStorePassword"].toString()
            keyPassword = keystoreProperties["debugKeyPassword"].toString()
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/NOTICE.md",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/**",
                "DebugProbesKt.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            fixComposeResourcesStructure,
            FixComposeResTask::outputDirectory
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.base)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.adaptive.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.slf4j.android)
    implementation(libs.material)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":app:shared"))
    implementation(project(":utils:shared"))
}