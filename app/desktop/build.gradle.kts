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
val dmgBackgroundDir = layout.projectDirectory.dir("dmg-background")
val dmgStageDir = layout.buildDirectory.dir("dmg-background/stage")
val dmgDistDir = layout.buildDirectory.dir("dmg-background/dist")
val debugAppBundle = layout.buildDirectory.dir("compose/binaries/main/app/FromChat.app")
val releaseAppBundle = layout.buildDirectory.dir("compose/binaries/main-release/app/FromChat.app")
val testDmgOutput = layout.buildDirectory.file("distributions/FromChat-test.dmg")
val releaseDmgOutput = layout.buildDirectory.file("distributions/FromChat.dmg")

fun resolvePackagingJdkHome(): String {
    System.getenv("FROMCHAT_PACKAGING_JDK")?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { home ->
        if (File(home, "bin/jpackage").isFile || File(home, "bin/jpackage.exe").isFile) {
            return home
        }
    }
    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val candidates = buildList {
        if (os.contains("mac")) {
            add("$userHome/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.19+10/Contents/Home")
            add("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home")
            add("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home")
            add("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
            add("/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
        }
        if (os.contains("win")) {
            add("C:/Program Files/Eclipse Adoptium/jdk-17")
            add("C:/Program Files/Eclipse Adoptium/jdk-21")
            add("C:/Program Files/Java/jdk-17")
            add("C:/Program Files/Microsoft/jdk-17")
        }
        if (os.contains("linux")) {
            add("/usr/lib/jvm/temurin-17-jdk-amd64")
            add("/usr/lib/jvm/temurin-21-jdk-amd64")
            add("/usr/lib/jvm/java-17-openjdk-amd64")
            add("/usr/lib/jvm/java-21-openjdk-amd64")
            add("/usr/lib/jvm/java-17-openjdk")
            add("/usr/lib/jvm/java-21-openjdk")
        }
        System.getProperty("java.home")?.let { add(it) }
    }
    return candidates.firstOrNull {
        File(it, "bin/jpackage").isFile || File(it, "bin/jpackage.exe").isFile
    } ?: error(
        "No JDK with jpackage found. Install Temurin 17+ or set FROMCHAT_PACKAGING_JDK.",
    )
}

compose.desktop {
    application {
        mainClass = "ru.fromchat.desktop.MainKt"
        javaHome = resolvePackagingJdkHome()

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            packageName = "FromChat"
            packageVersion = rootProject.extra["versionName"] as String
            description = "FromChat desktop"
            copyright = "© FromChat"
            includeAllModules = true

            // macOS DMG uses the custom create-dmg pipeline (packageReleaseDmg), not jpackage.
            // Linux .AppImage is built via appimagetool (packageLinuxAppImage), not TargetFormat.AppImage.
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            when {
                osName.contains("linux") -> {
                    targetFormats(
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                    )
                }
                osName.contains("win") -> {
                    // App-image via createReleaseDistributable; custom Rust setup wraps it.
                    targetFormats(
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                    )
                }
            }

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

val runningOnMacOs = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

val installDmgBackgroundTools = tasks.register<Exec>("installDmgBackgroundTools") {
    onlyIf { runningOnMacOs }
    workingDir = dmgBackgroundDir.asFile
    inputs.file(dmgBackgroundDir.file("package.json"))
    inputs.file(dmgBackgroundDir.file("package-lock.json"))
    outputs.dir(dmgBackgroundDir.dir("node_modules/playwright"))
    commandLine(
        "bash",
        "-lc",
        """
        if [ ! -f node_modules/playwright/package.json ]; then
          npm install --no-fund --no-audit
          npx playwright install chromium
        fi
        """.trimIndent(),
    )
}

val stageDmgBackground = tasks.register<Copy>("stageDmgBackground") {
    onlyIf { runningOnMacOs }
    dependsOn(":app:shared:prepareComposeResourcesTaskForCommonMain")
    mustRunAfter(installDmgBackgroundTools)
    from(dmgBackgroundDir) {
        exclude("node_modules/**")
    }
    into(dmgStageDir)
    doLast {
        val sharedLogo = rootProject.project(":app:shared").layout.buildDirectory
            .file("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/drawable/logo_square.png")
            .get().asFile
        if (sharedLogo.isFile) {
            val target = dmgStageDir.get().asFile.resolve("assets/logo_square.png")
            target.parentFile.mkdirs()
            sharedLogo.copyTo(target, overwrite = true)
        } else {
            logger.lifecycle("Compose logo not generated; using bundled dmg-background/assets/logo_square.png")
        }
    }
}

val exportDmgBackground = tasks.register<Exec>("exportDmgBackground") {
    onlyIf { runningOnMacOs }
    dependsOn(stageDmgBackground, installDmgBackgroundTools)
    workingDir = dmgBackgroundDir.asFile
    val stage = dmgStageDir.get().asFile.absolutePath
    val dist = dmgDistDir.get().asFile.absolutePath
    inputs.dir(dmgStageDir)
    outputs.dir(dmgDistDir)
    commandLine(
        "bash",
        "-lc",
        "node export.mjs '${stage}' '${dist}'",
    )
}

fun org.gradle.api.tasks.TaskContainer.registerPackageDmgTask(
    name: String,
    appBundle: Provider<Directory>,
    dmgOutput: Provider<RegularFile>,
    distributableTask: String,
    scriptName: String,
) = register<Exec>(name) {
    onlyIf { runningOnMacOs }
    group = "compose desktop"
    dependsOn(distributableTask, exportDmgBackground)
    val positionsFile = dmgDistDir.map { it.file("icon-positions.json") }
    val dmgScript = layout.buildDirectory.file("dmg-background/$scriptName")
    inputs.dir(appBundle)
    inputs.dir(dmgDistDir)
    inputs.file(positionsFile)
    outputs.file(dmgOutput)
    doFirst {
        val app = appBundle.get().asFile
        check(app.isDirectory) {
            "Missing ${app.absolutePath}. Run :app:desktop:$distributableTask first."
        }
        val positions = positionsFile.get().asFile
        check(positions.isFile) { "Missing ${positions.absolutePath}" }
        val script = dmgScript.get().asFile
        script.parentFile.mkdirs()
        script.writeText(
            """
            #!/usr/bin/env bash
            set -euo pipefail
            POSITIONS='${positions.absolutePath}'
            APP='${app.absolutePath}'
            OUT='${dmgOutput.get().asFile.absolutePath}'
            DIST='${dmgDistDir.get().asFile.absolutePath}'
            STAGING="${'$'}{TMPDIR:-/tmp}/fromchat-dmg-staging-${'$'}RANDOM"
            rm -rf "${'$'}OUT" "${'$'}STAGING"
            mkdir -p "${'$'}STAGING"
            cp -R "${'$'}APP" "${'$'}STAGING/"
            WINDOW_SIZE=($(node -e "const c=require('${'$'}POSITIONS').createDmg; console.log(c.windowSize.join(' '))"))
            ICON_SIZE=$(node -e "console.log(require('${'$'}POSITIONS').createDmg.iconSize)")
            APP_POS=($(node -e "const i=require('${'$'}POSITIONS').createDmg.icons.find(x=>x[0]==='FromChat.app'); console.log(i[1], i[2])"))
            APPS_POS=($(node -e "const i=require('${'$'}POSITIONS').createDmg.icons.find(x=>x[0]==='Applications'); console.log(i[1], i[2])"))
            (
              cd "${'$'}DIST"
              create-dmg \
                --volname "FromChat" \
                --window-size "${'$'}{WINDOW_SIZE[0]}" "${'$'}{WINDOW_SIZE[1]}" \
                --icon-size "${'$'}ICON_SIZE" \
                --icon "FromChat.app" "${'$'}{APP_POS[0]}" "${'$'}{APP_POS[1]}" \
                --hide-extension "FromChat.app" \
                --app-drop-link "${'$'}{APPS_POS[0]}" "${'$'}{APPS_POS[1]}" \
                --app-drop-link-name "Программы" \
                --text-size 14 \
                --background "dmg-background@2x.png" \
                "${'$'}OUT" \
                "${'$'}STAGING"
            )
            rm -rf "${'$'}STAGING"
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        commandLine(script.absolutePath)
    }
}

tasks.registerPackageDmgTask(
    name = "packageTestDmg",
    appBundle = debugAppBundle,
    dmgOutput = testDmgOutput,
    distributableTask = "createDistributable",
    scriptName = "package-test-dmg.sh",
).configure {
    description = "Build debug app bundle, export DMG background, and package FromChat-test.dmg."
}

tasks.registerPackageDmgTask(
    name = "packageReleaseDmg",
    appBundle = releaseAppBundle,
    dmgOutput = releaseDmgOutput,
    distributableTask = "createReleaseDistributable",
    scriptName = "package-release-dmg.sh",
).configure {
    description =
        "Build release app (ProGuard + jpackage), export DMG background, and package FromChat.dmg. ProGuard may take 10–20 minutes."
}

tasks.register("packageDmg") {
    group = "compose desktop"
    description = "Alias for packageReleaseDmg (full release DMG pipeline)."
    dependsOn("packageReleaseDmg")
}

val desktopVersionName = rootProject.extra["versionName"] as String
val desktopDistDir = layout.buildDirectory.dir("distributions/release")
val runningOnLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
val runningOnWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

val releaseAppImageDir = layout.buildDirectory.dir("compose/binaries/main-release/app/FromChat")
val linuxAppImageOutput = desktopDistDir.map { it.file("FromChat-$desktopVersionName-linux.AppImage") }
val windowsSetupOutput = desktopDistDir.map { it.file("FromChat-Setup-$desktopVersionName.exe") }
val macDmgReleaseOutput = desktopDistDir.map { it.file("FromChat-$desktopVersionName-macOS.dmg") }

tasks.register("packageReleaseMac") {
    group = "compose desktop"
    description = "Build release macOS DMG and copy to distributions/release."
    onlyIf { runningOnMacOs }
    dependsOn("packageReleaseDmg")
    outputs.file(macDmgReleaseOutput)
    doLast {
        val outDir = desktopDistDir.get().asFile
        outDir.mkdirs()
        val src = releaseDmgOutput.get().asFile
        check(src.isFile) { "Missing ${src.absolutePath}" }
        src.copyTo(macDmgReleaseOutput.get().asFile, overwrite = true)
    }
}

val packageLinuxAppImage = tasks.register<Exec>("packageLinuxAppImage") {
    group = "compose desktop"
    description = "Build a Linux .AppImage from the release app-image using appimagetool."
    onlyIf { runningOnLinux }
    dependsOn("createReleaseDistributable")
    inputs.dir(releaseAppImageDir)
    outputs.file(linuxAppImageOutput)
    doFirst {
        val appDir = releaseAppImageDir.get().asFile
        check(appDir.isDirectory) { "Missing ${appDir.absolutePath}" }
        val out = linuxAppImageOutput.get().asFile
        out.parentFile.mkdirs()
        val stage = layout.buildDirectory.get().asFile.resolve("appimage-stage/FromChat.AppDir")
        stage.deleteRecursively()
        stage.mkdirs()
        appDir.copyRecursively(stage, overwrite = true)
        val nestedExe = sequenceOf(
            stage.resolve("FromChat"),
            stage.resolve("bin/FromChat"),
        ).firstOrNull { it.isFile }
            ?: stage.walkTopDown().firstOrNull { it.isFile && it.name == "FromChat" && !it.path.contains("/runtime/") }
            ?: error("FromChat executable not found under ${appDir.absolutePath}")
        val appRun = stage.resolve("AppRun")
        val rel = stage.toPath().relativize(nestedExe.toPath()).toString()
        appRun.writeText(
            """
            #!/bin/sh
            SELF="${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")"
            exec "${'$'}SELF/$rel" "${'$'}@"
            """.trimIndent() + "\n",
        )
        appRun.setExecutable(true)
        nestedExe.setExecutable(true)
        stage.resolve("fromchat.desktop").writeText(
            """
            [Desktop Entry]
            Name=FromChat
            Exec=AppRun
            Icon=fromchat
            Type=Application
            Categories=Network;InstantMessaging;
            """.trimIndent() + "\n",
        )
        val iconSrc = desktopWindowIconPng.asFile
        if (iconSrc.isFile) {
            iconSrc.copyTo(stage.resolve("fromchat.png"), overwrite = true)
        }
        commandLine(
            "bash",
            "-lc",
            """
            set -euo pipefail
            TOOL="${'$'}{APPIMAGETOOL:-appimagetool}"
            if ! command -v "${'$'}TOOL" >/dev/null 2>&1; then
              echo "appimagetool not found. Install it or set APPIMAGETOOL." >&2
              exit 1
            fi
            ARCH="${'$'}(uname -m)"
            export ARCH
            "${'$'}TOOL" "${stage.absolutePath}" "${out.absolutePath}"
            """.trimIndent(),
        )
    }
}

tasks.register("packageReleaseLinux") {
    group = "compose desktop"
    description = "Build release Linux deb, rpm, and AppImage."
    onlyIf { runningOnLinux }
    if (runningOnLinux) {
        dependsOn("packageReleaseDeb", "packageReleaseRpm", packageLinuxAppImage)
    }
    doLast {
        val outDir = desktopDistDir.get().asFile
        outDir.mkdirs()
        val binaries = layout.buildDirectory.get().asFile.resolve("compose/binaries/main-release")
        binaries.walkTopDown()
            .filter { it.isFile && (it.extension == "deb" || it.extension == "rpm") }
            .forEach { file ->
                val renamed = "FromChat-$desktopVersionName-linux.${file.extension}"
                file.copyTo(outDir.resolve(renamed), overwrite = true)
            }
    }
}

val windowsSetupDir = layout.projectDirectory.dir("windows-setup")
val windowsRustReleaseDir = windowsSetupDir.dir("target/release")
val windowsRustBinaryNames = listOf(
    "fromchat-setup.exe",
    "fromchat-setup-helper.exe",
    "fromchat-portable-launcher.exe",
    "fromchat-pack.exe",
)

val buildWindowsSetupRust = tasks.register<Exec>("buildWindowsSetupRust") {
    group = "compose desktop"
    description = "Build Rust setup, helper, and portable launcher (Windows only)."
    onlyIf { runningOnWindows }
    workingDir = windowsSetupDir.asFile
    commandLine("cargo", "build", "--release", "--workspace")
    inputs.dir(windowsSetupDir.dir("setup"))
    inputs.dir(windowsSetupDir.dir("helper"))
    inputs.dir(windowsSetupDir.dir("portable-launcher"))
    inputs.dir(windowsSetupDir.dir("common"))
    inputs.dir(windowsSetupDir.dir("pack"))
    inputs.dir(windowsSetupDir.dir("assets"))
    inputs.file(windowsSetupDir.file("Cargo.toml"))
    windowsRustBinaryNames.forEach { name ->
        outputs.file(windowsRustReleaseDir.file(name))
    }
}

fun Exec.configureWindowsPackTask() {
    inputs.dir(releaseAppImageDir).withPropertyName("appImage")
    inputs.dir(windowsRustReleaseDir).withPropertyName("windowsSetupRust")
    inputs.property("packVersion", desktopVersionName)
    outputs.file(windowsSetupOutput)
    doFirst {
        val appDir = releaseAppImageDir.get().asFile
        check(appDir.isDirectory) { "Missing ${appDir.absolutePath}" }
        val outDir = desktopDistDir.get().asFile
        outDir.mkdirs()
        val releaseDir = windowsRustReleaseDir.asFile
        val packer = listOf("fromchat-pack.exe", "fromchat-pack")
            .map { releaseDir.resolve(it) }
            .firstOrNull { it.isFile }
            ?: error("Missing fromchat-pack. Build windows-setup workspace first.")
        val setupBin = releaseDir.resolve("fromchat-setup.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("fromchat-setup")
        val helperBin = releaseDir.resolve("fromchat-setup-helper.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("fromchat-setup-helper")
        val launcherBin = releaseDir.resolve("fromchat-portable-launcher.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("fromchat-portable-launcher")
        commandLine(
            packer.absolutePath,
            "--app-image",
            appDir.absolutePath,
            "--version",
            desktopVersionName,
            "--setup-out",
            windowsSetupOutput.get().asFile.absolutePath,
            "--setup-bin",
            setupBin.absolutePath,
            "--helper-bin",
            helperBin.absolutePath,
            "--launcher-bin",
            launcherBin.absolutePath,
        )
    }
}

tasks.register<Exec>("packSetupOnly") {
    group = "compose desktop"
    description = "Repack setup EXE from existing app-image + Rust (skips ProGuard)."
    onlyIf { runningOnWindows }
    dependsOn(buildWindowsSetupRust)
    configureWindowsPackTask()
}

tasks.register<Exec>("packageReleaseWindows") {
    group = "compose desktop"
    description = "Build release Windows app-image, then setup EXE."
    onlyIf { runningOnWindows }
    if (runningOnWindows) {
        dependsOn("createReleaseDistributable", buildWindowsSetupRust)
    }
    configureWindowsPackTask()
}

tasks.register("packageReleaseDesktop") {
    group = "compose desktop"
    description = "Build the release desktop package for the current OS."
    when {
        runningOnMacOs -> dependsOn("packageReleaseMac")
        runningOnLinux -> dependsOn("packageReleaseLinux")
        runningOnWindows -> dependsOn("packageReleaseWindows")
        else -> doFirst { error("Unsupported OS for packageReleaseDesktop") }
    }
}

