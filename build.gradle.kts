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
extra["versionName"] = providers.gradleProperty("versionName").getOrElse("1.2.1")
extra["versionCode"] = providers.gradleProperty("versionCode").getOrElse("121999").toInt()

fun parseGitHubRepositoryFromRemote(remote: String): String {
    if (remote.isBlank()) return ""
    val normalized = remote.trim().replace('\\', '/')
    return Regex("""github\.com[/:]([^/]+)/([^/]+?)(?:\.git)?/?$""", RegexOption.IGNORE_CASE)
        .find(normalized)
        ?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
        .orEmpty()
}

/** CI: ORG_GRADLE_PROJECT_* from setup-java-gradle / set-version; local builds fall back to git. */
extra["gitCommitSha"] = providers.gradleProperty("gitCommitSha")
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse(
        providers.exec {
            workingDir = rootDir
            commandLine("git", "rev-parse", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() },
    )
    .getOrElse("")

extra["gitHubRepository"] = providers.gradleProperty("gitHubRepository")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
    .orElse(
        providers.exec {
            workingDir = rootDir
            commandLine("git", "remote", "get-url", "origin")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { parseGitHubRepositoryFromRemote(it.trim()) },
    )
    .getOrElse("")

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.google.services)
    }
}

/**
 * Packaging jobs restore JVM outputs from the Linux Build JAR artifact. Gradle has no local
 * execution history for those outputs on macOS/Windows, so it would re-run compileKotlinJvm unless
 * we skip it when the restored tree is already present.
 */
fun Project.useCiPrebuiltJvm(): Boolean =
    providers.environmentVariable("FROMCHAT_PREBUILT_JVM").orNull == "1"

fun Project.hasFilesUnder(relativeDir: String, extension: String): Boolean {
    val root = layout.buildDirectory.dir(relativeDir).get().asFile
    return root.isDirectory && root.walk().any { it.isFile && it.extension == extension }
}

subprojects {
    afterEvaluate {
        if (!useCiPrebuiltJvm()) return@afterEvaluate
        when (path) {
            ":app:shared" -> {
                tasks.matching { it.name == "compileKotlinJvm" }.configureEach {
                    onlyIf {
                        val skip = hasFilesUnder("classes/kotlin/jvm/main", "class")
                        if (skip) {
                            logger.lifecycle("$path compileKotlinJvm skipped — CI prebuilt JVM classes")
                        }
                        !skip
                    }
                }
            }
            ":app:desktop" -> {
                tasks.matching { it.name == "compileKotlin" }.configureEach {
                    onlyIf {
                        val skip = hasFilesUnder("classes/kotlin/main", "class")
                        if (skip) {
                            logger.lifecycle("$path compileKotlin skipped — CI prebuilt JVM classes")
                        }
                        !skip
                    }
                }
            }
        }
        if (path == ":app:shared") {
            tasks.named("generateAppBuildInfo").configure {
                onlyIf {
                    val skip = hasFilesUnder("generated/sources/appBuildInfo/kotlin", "kt")
                    if (skip) {
                        logger.lifecycle("generateAppBuildInfo skipped — CI prebuilt output")
                    }
                    !skip
                }
            }
        }
    }
}