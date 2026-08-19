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
                dockName = "FromChat"
                iconFile.set(desktopWindowIconIcns)
                // Tray icons as NSImage templates (menu bar light/dark tint). Also set in Main.kt.
                jvmArgs("-Dapple.awt.enableTemplateImages=true")
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>FromChat</string>
                        <key>NSUserNotificationAlertStyle</key>
                        <string>banner</string>
                        <key>LSApplicationCategoryType</key>
                        <string>public.app-category.social-networking</string>
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

val macNotificationsSource = layout.projectDirectory.file("src/nativeDarwin/MacNotificationCenter.m")
val macNotificationsDylib = layout.buildDirectory.file("natives/libfromchat_notifications.dylib")
val compileMacNotifications = tasks.register<Exec>("compileMacNotifications") {
    onlyIf {
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    }
    inputs.file(macNotificationsSource)
    outputs.file(macNotificationsDylib)
    doFirst {
        macNotificationsDylib.get().asFile.parentFile.mkdirs()
    }
    val jniInclude = listOfNotNull(
        System.getenv("JAVA_HOME")?.let { "$it/include" },
        "${System.getProperty("java.home")}/include",
        "/opt/homebrew/opt/openjdk/include",
        "/opt/homebrew/opt/openjdk@26/include",
        "/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home/include",
    ).first { file("$it/jni.h").isFile }
    commandLine(
        "clang",
        "-shared",
        "-fobjc-arc",
        "-fobjc-exceptions",
        "-fPIC",
        "-mmacosx-version-min=11.0",
        "-arch", "arm64",
        "-arch", "x86_64",
        "-framework", "Foundation",
        "-framework", "AppKit",
        "-framework", "UserNotifications",
        "-framework", "CoreGraphics",
        "-framework", "CoreServices",
        "-I", jniInclude,
        "-I", "$jniInclude/darwin",
        "-o", macNotificationsDylib.get().asFile.absolutePath,
        macNotificationsSource.asFile.absolutePath,
    )
}

tasks.named<Copy>("processResources") {
    dependsOn(compileMacNotifications)
    from(layout.buildDirectory.dir("natives")) {
        include("*.dylib")
        into("natives")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

/**
 * Compose Desktop puts the whole runtime classpath on the unnamed module path.
 * JavaFX 11+ must be loaded as named modules via --module-path / --add-modules,
 * and those jars must not also sit on the regular classpath.
 *
 * On macOS, [UNUserNotificationCenter] aborts a bare `java` process
 * (`bundleProxyForCurrentProcess is nil`). :run is therefore launched from a
 * FromChat.app wrapper so Notification Center sees `ru.fromchat.desktop`.
 */
fun JavaExec.configureFromChatDesktopJvm() {
    val javafxJars = classpath.files.filter { file ->
        javafxModules.any { module -> file.name.startsWith("javafx-$module-") }
    }
    classpath = project.files(classpath.files.filterNot { it in javafxJars.toSet() })
    val dockIconArgs = buildList {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (os.contains("mac")) {
            val png = desktopWindowIconPng.asFile
            val icns = desktopWindowIconIcns.asFile
            when {
                icns.isFile -> add("-Xdock:icon=${icns.absolutePath}")
                png.isFile -> add("-Xdock:icon=${png.absolutePath}")
            }
            add("-Xdock:name=FromChat")
        }
    }
    jvmArgs(
        dockIconArgs + listOf(
            "-Dapple.awt.enableTemplateImages=true",
            "--module-path",
            javafxJars.joinToString(File.pathSeparator) { it.absolutePath },
            "--add-modules",
            "javafx.controls,javafx.web,javafx.swing,javafx.media,javafx.graphics,javafx.base",
            "--add-opens",
            "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        ),
    )
}

fun prepareFromChatDevApp(javaHome: File, destApp: File) {
    val macosDir = destApp.resolve("Contents/MacOS")
    val resourcesDir = destApp.resolve("Contents/Resources")
    macosDir.mkdirs()
    resourcesDir.mkdirs()
    val destExec = macosDir.resolve("FromChat")
    val stamp = resourcesDir.resolve("java-home.txt")
    val javaHomePath = javaHome.absolutePath
    if (!destExec.isFile || stamp.takeIf { it.isFile }?.readText() != javaHomePath) {
        javaHome.resolve("bin/java").copyTo(destExec, overwrite = true)
        destExec.setExecutable(true, false)
        ProcessBuilder(
            "install_name_tool",
            "-add_rpath",
            javaHome.resolve("lib").absolutePath,
            destExec.absolutePath,
        ).inheritIO().start().waitFor()
        stamp.writeText(javaHomePath)
    }
    destApp.resolve("Contents/Info.plist").writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>CFBundleExecutable</key>
            <string>FromChat</string>
            <key>CFBundleIdentifier</key>
            <string>ru.fromchat.desktop</string>
            <key>CFBundleName</key>
            <string>FromChat</string>
            <key>CFBundleDisplayName</key>
            <string>FromChat</string>
            <key>CFBundlePackageType</key>
            <string>APPL</string>
            <key>CFBundleShortVersionString</key>
            <string>1.0.0</string>
            <key>CFBundleVersion</key>
            <string>1</string>
            <key>CFBundleIconFile</key>
            <string>app_window_icon.icns</string>
            <key>LSMinimumSystemVersion</key>
            <string>11.0</string>
            <key>LSApplicationCategoryType</key>
            <string>public.app-category.social-networking</string>
            <key>NSHighResolutionCapable</key>
            <true/>
            <key>NSUserNotificationAlertStyle</key>
            <string>banner</string>
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
        </dict>
        </plist>
        """.trimIndent() + "\n",
    )
    destApp.resolve("Contents/PkgInfo").writeText("APPL????")
    val icns = desktopWindowIconIcns.asFile
    if (icns.isFile) {
        icns.copyTo(resourcesDir.resolve("app_window_icon.icns"), overwrite = true)
    }
    ProcessBuilder(
        "codesign",
        "--force",
        "--sign",
        "-",
        "--identifier",
        "ru.fromchat.desktop",
        destApp.absolutePath,
    ).inheritIO().start().waitFor()
}

val runningOnMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

tasks.matching { it.name == "run" }.configureEach {
    if (this !is JavaExec) return@configureEach
    if (runningOnMac) return@configureEach
    doFirst { configureFromChatDesktopJvm() }
}

afterEvaluate {
    if (!runningOnMac) return@afterEvaluate
    val runTask = tasks.findByName("run") as? JavaExec ?: return@afterEvaluate
    runTask.actions.clear()
    runTask.doFirst { runTask.configureFromChatDesktopJvm() }
    runTask.doLast {
        val javaHomeFile = runTask.javaLauncher.orNull?.metadata?.installationPath?.asFile
            ?: file(System.getProperty("java.home"))
        val app = layout.buildDirectory.get().asFile.resolve("macos-dev-bundle/FromChat.app")
        prepareFromChatDevApp(javaHomeFile, app)
        val command = buildList {
            add(app.resolve("Contents/MacOS/FromChat").absolutePath)
            addAll(runTask.allJvmArgs)
            add("-classpath")
            add(runTask.classpath.asPath)
            add(runTask.mainClass.get())
            addAll(runTask.args)
        }
        val process = ProcessBuilder(command)
            .directory(runTask.workingDir)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
        process.environment().putAll(runTask.environment.mapValues { it.value.toString() })
        process.environment()["JAVA_HOME"] = javaHomeFile.absolutePath
        val exit = process.start().waitFor()
        if (exit != 0) {
            throw GradleException("FromChat exited with $exit")
        }
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
