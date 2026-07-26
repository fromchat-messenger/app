plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val javafxVersion = libs.versions.openjfx.get()
val javafxClassifier = openjfxClassifier()
val javafxModules = listOf("base", "graphics", "controls", "media", "web", "swing")

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(project(":app:shared"))
    implementation(project(":utils:shared"))
    implementation(libs.kotlinx.coroutines.swing)
    // OpenJFX publishes empty jars without a classifier; declare every module
    // with the host classifier and exclude transitive stubs.
    javafxModules.forEach { module ->
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxClassifier") {
            exclude(group = "org.openjfx")
        }
    }
}

val desktopWindowIconPng = layout.projectDirectory.file("src/main/resources/app_window_icon.png")
val desktopWindowIconIcns = layout.projectDirectory.file("icons/app_window_icon.icns")

compose.desktop {
    application {
        mainClass = "ru.fromchat.desktop.MainKt"

        nativeDistributions {
            packageName = "FromChat"
            packageVersion = "1.0.0"
            description = "FromChat desktop"
            copyright = "© FromChat"

            modules(
                "javafx.base",
                "javafx.graphics",
                "javafx.controls",
                "javafx.media",
                "javafx.web",
                "javafx.swing",
            )

            // Without iconFile, packaged apps (and macOS dock via jpackage) use Compose's default logo.
            linux {
                iconFile.set(desktopWindowIconPng)
            }
            windows {
                iconFile.set(desktopWindowIconPng)
            }
            macOS {
                bundleID = "ru.fromchat.desktop"
                iconFile.set(desktopWindowIconIcns)
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                          <dict>
                            <key>CFBundleURLName</key>
                            <string>FromChat</string>
                            <key>CFBundleURLSchemes</key>
                            <array>
                              <string>fromchat</string>
                            </array>
                          </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

/**
 * Compose Desktop puts the whole runtime classpath on the unnamed module path.
 * JavaFX 11+ must be loaded as named modules via --module-path / --add-modules,
 * and those jars must not also sit on the regular classpath.
 */
tasks.matching { it.name == "run" }.configureEach {
    if (this !is JavaExec) return@configureEach
    doFirst {
        val javafxJars = classpath.files.filter { file ->
            javafxModules.any { module -> file.name.startsWith("javafx-$module-") }
        }
        classpath = files(classpath.files.filterNot { it in javafxJars.toSet() })
        val dockIconArgs = buildList {
            // Compose only adds -Xdock:icon when macOS.iconFile is set; pin PNG for :run too.
            val os = System.getProperty("os.name").orEmpty().lowercase()
            if (os.contains("mac")) {
                val png = desktopWindowIconPng.asFile
                val icns = desktopWindowIconIcns.asFile
                when {
                    icns.isFile -> add("-Xdock:icon=${icns.absolutePath}")
                    png.isFile -> add("-Xdock:icon=${png.absolutePath}")
                }
            }
        }
        jvmArgs(
            dockIconArgs + listOf(
                "--module-path",
                javafxJars.joinToString(File.pathSeparator) { it.absolutePath },
                "--add-modules",
                "javafx.controls,javafx.web,javafx.swing,javafx.media,javafx.graphics,javafx.base",
                "--add-opens",
                "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
            ),
        )
    }
}

private fun openjfxClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isArm = arch == "aarch64" || arch == "arm64"
    return when {
        os.contains("mac") && isArm -> "mac-aarch64"
        os.contains("mac") -> "mac"
        os.contains("win") -> "win"
        isArm -> "linux-aarch64"
        else -> "linux"
    }
}
