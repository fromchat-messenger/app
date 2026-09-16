package ru.fromchat.plugins

enum class AppEvent {
    START,
    STOP,
    PAUSE,
    RESUME,
}

enum class HookStrategy {
    DEFAULT,
    CANCEL,
    MODIFY,
    MODIFY_FINAL,
}

data class HookResult<T>(
    val strategy: HookStrategy = HookStrategy.DEFAULT,
    val value: T? = null,
)

data class SendMessageHookContext(
    var text: String,
    val isDm: Boolean,
    val recipientId: Int?,
)

enum class MenuItemType {
    MESSAGE_CONTEXT,
    CHAT_ACTION,
    PROFILE_ACTION,
    SETTINGS_ROW,
}

data class MenuItemData(
    val menuType: MenuItemType,
    val text: String,
    val subtext: String? = null,
    val priority: Int = 0,
    val onClickKey: String = "",
)

sealed class PluginSetting {
    data class Header(val text: String) : PluginSetting()
    data class Switch(
        val key: String,
        val text: String,
        val default: Boolean = false,
        val subtext: String? = null,
    ) : PluginSetting()
    data class Input(
        val key: String,
        val text: String,
        val default: String = "",
        val subtext: String? = null,
    ) : PluginSetting()
    data class Text(
        val text: String,
        val subtext: String? = null,
    ) : PluginSetting()
}

data class PluginManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String = "",
    val version: String = "1.0.0",
    val appVersion: String = "",
    val sdkVersion: String = "",
    val entryClass: String,
    val androidArtifact: String? = null,
    val desktopArtifact: String? = null,
)
