package ru.fromchat.plugins.host

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.plugins.PluginManifest

object PluginManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): PluginManifest {
        val root = json.parseToJsonElement(raw).jsonObject
        val artifacts = root["artifacts"]?.jsonObject
        return PluginManifest(
            id = root.requireString("id"),
            name = root.requireString("name"),
            description = root["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = root["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            version = root["version"]?.jsonPrimitive?.contentOrNull ?: "1.0.0",
            usage = root["usage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            appVersion = root["app_version"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sdkVersion = root["sdk_version"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            entryClass = root.requireString("entry_class"),
            androidArtifact = artifacts?.get("android")?.jsonPrimitive?.contentOrNull,
            desktopArtifact = artifacts?.get("desktop")?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: error("manifest missing $key")
}
