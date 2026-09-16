plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":plugins:sdk"))
}

tasks.register("packageFcPlugin") {
    group = "plugins"
    description = "Build hello_world.fcplugin with desktop JAR"
    dependsOn(tasks.named("compileKotlin"))
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val staging = buildDir.resolve("fcplugin-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        staging.resolve("desktop").mkdirs()
        staging.resolve("android").mkdirs()

        val jvmClasses = buildDir.resolve("classes/kotlin/main")
        val jarFile = staging.resolve("desktop/plugin.jar")
        val jarProcess = ProcessBuilder(
            "jar",
            "cf",
            jarFile.absolutePath,
            "-C",
            jvmClasses.absolutePath,
            ".",
        ).inheritIO().start()
        check(jarProcess.waitFor() == 0) { "jar failed" }

        staging.resolve("manifest.json").writeText(
            """
            {
              "id": "hello_world",
              "name": "Hello World",
              "description": "Rewrites .hello commands into a friendly greeting",
              "author": "FromChat",
              "version": "1.0.0",
              "app_version": ">=1.0.0",
              "sdk_version": ">=1.0.0",
              "entry_class": "ru.fromchat.plugins.sample.hello.HelloWorldPlugin",
              "artifacts": {
                "android": "android/plugin.dex",
                "desktop": "desktop/plugin.jar"
              }
            }
            """.trimIndent(),
        )

        val archive = buildDir.resolve("distributions/hello_world.fcplugin")
        archive.parentFile.mkdirs()
        if (archive.exists()) archive.delete()
        val zipProcess = ProcessBuilder("zip", "-r", archive.absolutePath, ".")
            .directory(staging)
            .inheritIO()
            .start()
        check(zipProcess.waitFor() == 0) { "zip failed" }
        println("Created ${archive.absolutePath}")
    }
}
