plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

val javafxVersion = libs.versions.openjfx.get()
val javafxClassifier = openjfxClassifier()
val javafxModules = listOf("base", "graphics", "controls", "media", "web", "swing")

dependencies {
    if (isWindowsArm64Host()) {
        val composeDesktopVersion = libs.versions.compose.multiplatform.get()
        implementation("org.jetbrains.compose.desktop:desktop-jvm-windows-arm64:$composeDesktopVersion")
        implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.150.1")
        implementation("org.jetbrains.skiko:skiko-awt-runtime-angle-windows-arm64:0.150.1")
    } else {
        implementation(compose.desktop.currentOs)
    }
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.compose.components.resources)
    implementation(project(":app:shared"))
    implementation(project(":utils:shared"))
    implementation(libs.kotlinx.coroutines.swing)
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    // OpenJFX publishes empty jars without a classifier; declare every module
    // with the host classifier and exclude transitive stubs.
    javafxModules.forEach { module ->
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxClassifier") {
            exclude(group = "org.openjfx")
        }
    }
}

configurations.configureEach {
    if (isWindowsArm64Host()) {
        exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")
    }
}

val desktopWindowIconPng = layout.projectDirectory.file("src/main/resources/app_window_icon.png")
val desktopWindowIconIco = layout.projectDirectory.file("icons/app_window_icon.ico")
val desktopWindowIconIcns = layout.projectDirectory.file("icons/app_window_icon.icns")
val dmgBackgroundDir = layout.projectDirectory.dir("dmg-background")
val dmgStageDir = layout.buildDirectory.dir("dmg-background/stage")
val dmgDistDir = layout.buildDirectory.dir("dmg-background/dist")
val debugAppBundle = layout.buildDirectory.dir("compose/binaries/main/app/FromChat.app")
val releaseAppBundle = layout.buildDirectory.dir("compose/binaries/main-release/app/FromChat.app")
val testDmgOutput = layout.buildDirectory.file("distributions/FromChat-test.dmg")
val releaseDmgOutput = layout.buildDirectory.file("distributions/FromChat.dmg")

private fun hostCpuArch(): String {
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    if (arch.contains("aarch64") || arch == "arm64") return "aarch64"
    val nativeArch = System.getenv("PROCESSOR_ARCHITEW6432")?.uppercase()
        ?: System.getenv("PROCESSOR_ARCHITECTURE")?.uppercase()
    if (nativeArch == "ARM64") return "aarch64"
    return when {
        arch.contains("amd64") || arch == "x86_64" -> "x86_64"
        else -> arch
    }
}

private fun isWindowsArm64Host(): Boolean {
    if (project.findProperty("windowsArm64") != null) return true
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return os.contains("win") && hostCpuArch() == "aarch64"
}

/** CI / packaging label: `x64` or `arm64`. Override with `-PdesktopArch=…`. */
fun desktopReleaseArchLabel(): String {
    project.findProperty("desktopArch")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return when (hostCpuArch()) {
        "aarch64" -> "arm64"
        else -> "x64"
    }
}

private fun syncWindowsArm64SkikoNatives(appDir: File, classpath: Iterable<File>) {
    appDir.listFiles()
        ?.filter { it.name.startsWith("skiko", ignoreCase = true) && it.name.contains("x64", ignoreCase = true) }
        ?.forEach { it.delete() }

    val arm64Runtime = classpath.firstOrNull {
        it.name.contains("skiko-awt-runtime-windows-arm64", ignoreCase = true)
    } ?: error("Missing skiko-awt-runtime-windows-arm64 on runtime classpath")

    arm64Runtime.copyTo(appDir.resolve(arm64Runtime.name), overwrite = true)

    ZipFile(arm64Runtime).use { zip ->
        zip.entries().asIterator().forEach { entry ->
            if (entry.isDirectory) return@forEach
            val fileName = File(entry.name).name
            if (!fileName.startsWith("skiko", ignoreCase = true)) return@forEach
            zip.getInputStream(entry).use { input ->
                appDir.resolve(fileName).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    val angleRuntime = classpath.firstOrNull {
        it.name.contains("skiko-awt-runtime-angle-windows-arm64", ignoreCase = true)
    } ?: error("Missing skiko-awt-runtime-angle-windows-arm64 on runtime classpath")

    angleRuntime.copyTo(appDir.resolve(angleRuntime.name), overwrite = true)
    ZipFile(angleRuntime).use { zip ->
        zip.entries().asIterator().forEach { entry ->
            if (entry.isDirectory) return@forEach
            val fileName = File(entry.name).name
            if (!fileName.endsWith(".dll", ignoreCase = true)) return@forEach
            zip.getInputStream(entry).use { input ->
                appDir.resolve(fileName).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

private fun windowsSkikoJvmArgs(): List<String> =
    if (isWindowsArm64Host()) {
        // Windows ARM64: OpenGL is unsupported; ANGLE (D3D11) is the supported GPU path in Skiko.
        listOf("-Dskiko.rendering.angle.enabled=true")
    } else {
        listOf("-Dskiko.renderApi=OPENGL", "-Dsun.java2d.d3d=true")
    }

private fun jdkReportsArch(javaExe: File): String? {
    if (!javaExe.isFile) return null
    val props = ProcessBuilder(javaExe.absolutePath, "-XshowSettings:properties", "-version")
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()
    val line = props.lineSequence().firstOrNull { it.trimStart().startsWith("os.arch =") }
    return line?.substringAfter("=")?.trim()
}

private val PE_MACHINE_AMD64 = 0x8664
private val PE_MACHINE_ARM64 = 0xAA64

private fun peMachineType(file: File): Int {
    val bytes = file.readBytes()
    check(bytes.size >= 0x40) { "PE file too small: ${file.absolutePath}" }
    val peOffset = ByteBuffer.wrap(bytes, 0x3C, 4).order(ByteOrder.LITTLE_ENDIAN).int
    check(peOffset in 0 until bytes.size - 6) { "Invalid PE offset in ${file.name}" }
    return ByteBuffer.wrap(bytes, peOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
}

private fun expectedPeMachine(): Int =
    if (isWindowsArm64Host()) PE_MACHINE_ARM64 else PE_MACHINE_AMD64

private fun assertWindowsBinaryArch(file: File, label: String) {
    val actual = peMachineType(file)
    val expected = expectedPeMachine()
    check(actual == expected) {
        "$label has wrong architecture (PE machine=0x${actual.toString(16)}, " +
            "expected=0x${expected.toString(16)}): ${file.absolutePath}"
    }
}

private fun assertWindowsAppImageArch(appImageRoot: File) {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return
    val launcher = appImageRoot.resolve("${appImageRoot.name}.exe")
    val jli = appImageRoot.resolve("runtime/bin/jli.dll")
    check(launcher.isFile) { "Missing app launcher: $launcher" }
    check(jli.isFile) { "Missing bundled JRE: $jli" }
    assertWindowsBinaryArch(launcher, "App launcher")
    assertWindowsBinaryArch(jli, "Bundled JRE (jli.dll)")
}

fun resolvePackagingJdkHome(): String {
    System.getenv("FROMCHAT_PACKAGING_JDK")?.takeIf { it.isNotBlank() }?.let { candidate ->
        if (isWindowsArm64Host()) {
            val arch = jdkReportsArch(File(candidate, "bin/java.exe"))
            check(arch == "aarch64") {
                "FROMCHAT_PACKAGING_JDK must be ARM64 on Windows ARM64 (os.arch=$arch): $candidate"
            }
        }
        return candidate
    }
    System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { home ->
        if (File(home, "bin/jpackage").isFile || File(home, "bin/jpackage.exe").isFile) {
            if (isWindowsArm64Host()) {
                val arch = jdkReportsArch(File(home, "bin/java.exe"))
                check(arch == "aarch64") {
                    "JAVA_HOME must be ARM64 on Windows ARM64 (os.arch=$arch): $home"
                }
            }
            return home
        }
    }
    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val wantArm = hostCpuArch() == "aarch64"
    val candidates = buildList {
        if (os.contains("mac")) {
            add("$userHome/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.19+10/Contents/Home")
            add("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home")
            add("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home")
            add("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
            add("/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
        }
        if (os.contains("win")) {
            if (wantArm) {
                add("C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot-arm64")
                add("C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot-arm64")
            }
            File("C:/Program Files/Eclipse Adoptium").takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isDirectory && (!wantArm || it.name.contains("arm64", ignoreCase = true)) }
                ?.sortedByDescending { it.name }
                ?.forEach { add(it.absolutePath.replace('\\', '/')) }
            add("C:/Program Files/Java/jdk-17")
            add("C:/Program Files/Microsoft/jdk-17")
        }
        if (os.contains("linux")) {
            if (wantArm) {
                add("/usr/lib/jvm/temurin-17-jdk-aarch64")
                add("/usr/lib/jvm/java-17-openjdk-aarch64")
            }
            add("/usr/lib/jvm/temurin-17-jdk-amd64")
            add("/usr/lib/jvm/temurin-21-jdk-amd64")
            add("/usr/lib/jvm/java-17-openjdk-amd64")
            add("/usr/lib/jvm/java-21-openjdk-amd64")
            add("/usr/lib/jvm/java-17-openjdk")
            add("/usr/lib/jvm/java-21-openjdk")
        }
        System.getProperty("java.home")?.let { add(it) }
    }
    return candidates.firstOrNull { home ->
        val jpackage = File(home, "bin/jpackage.exe").takeIf { it.isFile }
            ?: File(home, "bin/jpackage").takeIf { it.isFile }
        if (jpackage == null) return@firstOrNull false
        if (!isWindowsArm64Host()) return@firstOrNull true
        jdkReportsArch(File(home, "bin/java.exe")) == "aarch64"
    } ?: error(
        if (isWindowsArm64Host()) {
            "No ARM64 JDK with jpackage found. Run scripts\\ensure-windows-arm64-jdk.cmd or set FROMCHAT_PACKAGING_JDK."
        } else {
            "No JDK with jpackage found. Install Temurin 17+ or set FROMCHAT_PACKAGING_JDK."
        },
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
            description =
                if (project.findProperty("betaDesktop") != null) "FromChat Beta" else "FromChat"
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
                // jpackage on Windows requires .ico; a PNG is ignored and the Java cup stays.
                iconFile.set(desktopWindowIconIco)
                jvmArgs(*windowsSkikoJvmArgs().toTypedArray())
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
        dockIconArgs + buildList {
            if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
                addAll(windowsSkikoJvmArgs())
            }
            addAll(
                listOf(
                    "-Dapple.awt.enableTemplateImages=true",
                    "--module-path",
                    javafxJars.joinToString(File.pathSeparator) { it.absolutePath },
                    "--add-modules",
                    "javafx.controls,javafx.web,javafx.swing,javafx.media,javafx.graphics,javafx.base",
                    "--add-opens",
                    "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
                ),
            )
        },
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
    val isArm = hostCpuArch() == "aarch64"
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
val debugAppImageDir = layout.buildDirectory.dir("compose/binaries/main/app/FromChat")
val linuxAppImageOutput = desktopDistDir.map {
    it.file("FromChat-$desktopVersionName-linux-${desktopReleaseArchLabel()}.AppImage")
}
val windowsSetupOutput = desktopDistDir.map {
    it.file("FromChat-Setup-$desktopVersionName-windows-${desktopReleaseArchLabel()}.exe")
}
val windowsUniversalSetupOutput = desktopDistDir.map {
    it.file("FromChat-Setup-$desktopVersionName-windows-universal.exe")
}
val windowsBetaSetupOutput = desktopDistDir.map { it.file("FromChat-Setup-$desktopVersionName-beta2.exe") }
val macDmgReleaseOutput = desktopDistDir.map {
    it.file("FromChat-$desktopVersionName-macOS-${desktopReleaseArchLabel()}.dmg")
}

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
                val arch = desktopReleaseArchLabel()
                val renamed = "FromChat-$desktopVersionName-linux-$arch.${file.extension}"
                file.copyTo(outDir.resolve(renamed), overwrite = true)
            }
    }
}

val windowsSetupDir = layout.projectDirectory.dir("windows-setup")
val windowsRustReleaseDir = windowsSetupDir.dir("target/release")
val windowsRustBinaryNames = listOf(
    "FromChat-Installer.exe",
    "FromChat-Installer-Helper.exe",
    "fromchat-portable-launcher.exe",
    "fromchat-pack.exe",
    "fromchat-icon-patch.exe",
)

fun findJpackageAppExe(appImageDir: File): File {
    val direct = listOf(
        appImageDir.resolve("FromChat.exe"),
        appImageDir.resolve("app/FromChat.exe"),
    )
    direct.firstOrNull { it.isFile }?.let { return it }
    return appImageDir.walkTopDown()
        .first { file ->
            file.isFile && file.name.equals("FromChat.exe", ignoreCase = true)
        }
}

val buildWindowsSetupRust = tasks.register<Exec>("buildWindowsSetupRust") {
    group = "compose desktop"
    description = "Build Rust setup, helper, and portable launcher (Windows only)."
    onlyIf { runningOnWindows }
    workingDir = windowsSetupDir.asFile
    commandLine("cargo", "build", "--release", "--workspace")
    environment("FROMCHAT_SETUP_VERSION", desktopVersionName)
    inputs.property("setupVersion", desktopVersionName)
    inputs.dir(windowsSetupDir.dir("setup"))
    inputs.dir(windowsSetupDir.dir("helper"))
    inputs.dir(windowsSetupDir.dir("portable-launcher"))
    inputs.dir(windowsSetupDir.dir("common"))
    inputs.dir(windowsSetupDir.dir("pack"))
    inputs.dir(windowsSetupDir.dir("icon-patch"))
    inputs.dir(windowsSetupDir.dir("assets"))
    inputs.file(windowsSetupDir.file("Cargo.toml"))
    doFirst {
        val releaseDir = windowsRustReleaseDir.asFile
        val keepNames = windowsRustBinaryNames.toSet()
        if (releaseDir.isDirectory) {
            releaseDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.extension.equals("exe", ignoreCase = true) &&
                    file.name !in keepNames
                ) {
                    file.delete()
                }
            }
        }
    }
    windowsRustBinaryNames.forEach { name ->
        outputs.file(windowsRustReleaseDir.file(name))
    }
}

val patchWindowsJpackageIcon = tasks.register<Exec>("patchWindowsJpackageIcon") {
    group = "compose desktop"
    description = "Embed branded icon into jpackage FromChat.exe (Task Manager)."
    onlyIf { runningOnWindows }
    dependsOn(buildWindowsSetupRust)
    mustRunAfter("createDistributable")
    doFirst {
        val appImageDir = debugAppImageDir.get().asFile
        check(appImageDir.isDirectory) { "Missing app image at ${appImageDir.absolutePath}" }
        val exe = findJpackageAppExe(appImageDir)
        val patcher = windowsRustReleaseDir.asFile.resolve("fromchat-icon-patch.exe")
        check(patcher.isFile) { "Missing $patcher — build windows-setup workspace first." }
        commandLine(patcher.absolutePath, exe.absolutePath)
    }
}

val patchWindowsJpackageReleaseIcon = tasks.register<Exec>("patchWindowsJpackageReleaseIcon") {
    group = "compose desktop"
    description = "Embed branded icon into release jpackage FromChat.exe."
    onlyIf { runningOnWindows }
    dependsOn(buildWindowsSetupRust)
    mustRunAfter("createReleaseDistributable")
    doFirst {
        val appImageDir = releaseAppImageDir.get().asFile
        check(appImageDir.isDirectory) { "Missing app image at ${appImageDir.absolutePath}" }
        val exe = findJpackageAppExe(appImageDir)
        val patcher = windowsRustReleaseDir.asFile.resolve("fromchat-icon-patch.exe")
        check(patcher.isFile) { "Missing $patcher — build windows-setup workspace first." }
        commandLine(patcher.absolutePath, exe.absolutePath)
    }
}

val windowsPrebuiltX64AppImage = layout.buildDirectory.dir("prebuilt/windows-x64/app/FromChat")
val windowsPrebuiltArm64AppImage = layout.buildDirectory.dir("prebuilt/windows-arm64/app/FromChat")

fun Exec.configureWindowsPackTask(
    appImageDir: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>?,
    setupOutput: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    registrationId: String = "FromChat",
    prebuiltOnly: Boolean = false,
) {
    appImageDir?.let { inputs.dir(it).withPropertyName("appImage") }
    inputs.dir(windowsRustReleaseDir).withPropertyName("windowsSetupRust")
    inputs.property("packVersion", desktopVersionName)
    inputs.property("registrationId", registrationId)
    inputs.property("prebuiltOnly", prebuiltOnly)
    outputs.file(setupOutput)
    doFirst {
        val nativeAppDir = appImageDir?.get()?.asFile
        if (!prebuiltOnly) {
            check(nativeAppDir != null && nativeAppDir.isDirectory) {
                "Missing ${nativeAppDir?.absolutePath}"
            }
        }
        val prebuiltX64 = windowsPrebuiltX64AppImage.get().asFile
        val prebuiltArm64 = windowsPrebuiltArm64AppImage.get().asFile
        desktopDistDir.get().asFile.mkdirs()
        val releaseDir = windowsRustReleaseDir.asFile
        val packer = listOf("fromchat-pack.exe", "fromchat-pack")
            .map { releaseDir.resolve(it) }
            .firstOrNull { it.isFile }
            ?: error("Missing fromchat-pack. Build windows-setup workspace first.")
        val setupBin = releaseDir.resolve("FromChat-Installer.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("FromChat-Installer")
        val helperBin = releaseDir.resolve("FromChat-Installer-Helper.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("FromChat-Installer-Helper")
        val launcherBin = releaseDir.resolve("fromchat-portable-launcher.exe").takeIf { it.isFile }
            ?: releaseDir.resolve("fromchat-portable-launcher")
        val packArgs = mutableListOf(
            packer.absolutePath,
            "--version",
            desktopVersionName,
            "--registration-id",
            registrationId,
            "--setup-out",
            setupOutput.get().asFile.absolutePath,
            "--setup-bin",
            setupBin.absolutePath,
            "--helper-bin",
            helperBin.absolutePath,
            "--launcher-bin",
            launcherBin.absolutePath,
        )
        var packed = false
        if (prebuiltX64.isDirectory) {
            packArgs += listOf("--app-image-x64", prebuiltX64.absolutePath)
            packed = true
        } else if (!prebuiltOnly && nativeAppDir != null && nativeAppDir.isDirectory && !isWindowsArm64Host()) {
            packArgs += listOf("--app-image-x64", nativeAppDir.absolutePath)
            packed = true
        }
        if (prebuiltArm64.isDirectory) {
            packArgs += listOf("--app-image-arm64", prebuiltArm64.absolutePath)
            packed = true
        } else if (!prebuiltOnly && nativeAppDir != null && nativeAppDir.isDirectory && isWindowsArm64Host()) {
            packArgs += listOf("--app-image-arm64", nativeAppDir.absolutePath)
            packed = true
        }
        check(packed) {
            if (prebuiltOnly) {
                "packUniversalWindows needs both prebuilt/windows-x64 and prebuilt/windows-arm64 app images"
            } else {
                "No Windows app-image payload for setup EXE"
            }
        }
        commandLine(packArgs)
    }
}

tasks.register<Exec>("packUniversalWindows") {
    group = "compose desktop"
    description = "Pack a universal Windows setup EXE from prebuilt x64 + arm64 app images."
    onlyIf { runningOnWindows }
    dependsOn(buildWindowsSetupRust)
    configureWindowsPackTask(
        appImageDir = null,
        setupOutput = windowsUniversalSetupOutput,
        prebuiltOnly = true,
    )
}

tasks.register<Exec>("packSetupOnly") {
    group = "compose desktop"
    description = "Repack setup EXE from existing app-image + Rust (skips ProGuard)."
    onlyIf { runningOnWindows }
    dependsOn(buildWindowsSetupRust)
    configureWindowsPackTask(
        appImageDir = releaseAppImageDir,
        setupOutput = windowsSetupOutput,
    )
}

tasks.register<Exec>("packageBetaWindows") {
    group = "compose desktop"
    description = "Build debug Windows app-image (no ProGuard) and beta setup EXE."
    onlyIf { runningOnWindows }
    dependsOn("createDistributable", buildWindowsSetupRust, patchWindowsJpackageIcon)
    configureWindowsPackTask(
        appImageDir = debugAppImageDir,
        setupOutput = windowsBetaSetupOutput,
        registrationId = "FromChat Beta",
    )
    doFirst {
        check(project.findProperty("betaDesktop") != null) {
            "Run with -PbetaDesktop (e.g. gradlew :app:desktop:packageBetaWindows -PbetaDesktop)"
        }
        if (isWindowsArm64Host()) {
            val packagingJdk = File(resolvePackagingJdkHome(), "bin/java.exe")
            val arch = jdkReportsArch(packagingJdk)
            check(arch == "aarch64") {
                "packageBetaWindows on Windows ARM64 requires ARM64 packaging JDK (os.arch=$arch): $packagingJdk"
            }
            val gradleJava = File(System.getProperty("java.home"), "bin/java.exe")
            val gradleArch = jdkReportsArch(gradleJava)
            check(gradleArch == "aarch64") {
                "Gradle must run on ARM64 JDK on Windows ARM64 (os.arch=$gradleArch). " +
                    "Run scripts\\ensure-windows-arm64-jdk.cmd before gradlew."
            }
        }
        val outDir = desktopDistDir.get().asFile
        if (outDir.isDirectory) {
            outDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.name.startsWith("FromChat-Setup-") &&
                    file.name.endsWith(".exe", ignoreCase = true) &&
                    file.name != windowsBetaSetupOutput.get().asFile.name
                ) {
                    file.delete()
                }
            }
        }
        if (isWindowsArm64Host()) {
            val appDir = debugAppImageDir.get().asFile.resolve("app")
            syncWindowsArm64SkikoNatives(appDir, configurations.runtimeClasspath.get().files)
        }
    }
}

afterEvaluate {
    tasks.matching { it.name == "createRuntimeImage" || it.name == "createReleaseRuntimeImage" }.configureEach {
        if (!isWindowsArm64Host()) return@configureEach
        inputs.property("packagingJdkHome", resolvePackagingJdkHome())
        doLast {
            val runtimeDir = when (name) {
                "createReleaseRuntimeImage" -> layout.buildDirectory.dir("compose/tmp/main-release/runtime")
                else -> layout.buildDirectory.dir("compose/tmp/main/runtime")
            }
            val jli = runtimeDir.get().asFile.resolve("bin/jli.dll")
            check(jli.isFile) { "Missing runtime image JRE: $jli" }
            assertWindowsBinaryArch(jli, "createRuntimeImage output")
        }
    }
    tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
        doLast {
            val appImageRoot = when (name) {
                "createReleaseDistributable" -> releaseAppImageDir.get().asFile
                else -> debugAppImageDir.get().asFile
            }
            assertWindowsAppImageArch(appImageRoot)
            if (!isWindowsArm64Host()) return@doLast
            val appDir = appImageRoot.resolve("app")
            syncWindowsArm64SkikoNatives(appDir, configurations.runtimeClasspath.get().files)
            val natives = appDir.listFiles()?.map { it.name }.orEmpty()
            check(natives.none { it.contains("windows-x64", ignoreCase = true) }) {
                "App image still contains x64 Skiko on Windows ARM64: $appDir"
            }
            check(natives.any { it.contains("windows-arm64", ignoreCase = true) }) {
                "App image is missing ARM64 Skiko runtime: $appDir"
            }
            check(natives.any { it.equals("libEGL.dll", ignoreCase = true) }) {
                "App image is missing ANGLE libEGL.dll: $appDir"
            }
            check(natives.any { it.equals("libGLESv2.dll", ignoreCase = true) }) {
                "App image is missing ANGLE libGLESv2.dll: $appDir"
            }
        }
    }
}

tasks.register<Exec>("packageReleaseWindows") {
    group = "compose desktop"
    description = "Build release Windows app-image, then setup EXE."
    onlyIf { runningOnWindows }
    if (runningOnWindows) {
        dependsOn("createReleaseDistributable", buildWindowsSetupRust, patchWindowsJpackageReleaseIcon)
    }
    configureWindowsPackTask(
        appImageDir = releaseAppImageDir,
        setupOutput = windowsSetupOutput,
    )
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

