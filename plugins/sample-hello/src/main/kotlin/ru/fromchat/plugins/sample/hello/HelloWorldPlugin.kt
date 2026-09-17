package ru.fromchat.plugins.sample.hello

import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.HookResult
import ru.fromchat.plugins.HookStrategy
import ru.fromchat.plugins.MenuItemData
import ru.fromchat.plugins.MenuItemType
import ru.fromchat.plugins.PluginSetting
import ru.fromchat.plugins.SendMessageHookContext

class HelloWorldPlugin : BasePlugin() {
    override fun onPluginLoad() {
        log("Hello World plugin loaded")
        showBulletin("Hello World plugin is active")
        registerUiOverrides()
        registerInjectedUi()
        registerRawHooks()
        addOnSendMessageHook { context ->
            onSendMessage(context)
        }
        hookShared(
            hookId = "logger.debug",
            before = { args ->
                val tag = args.getOrNull(0) as? String ?: return@hookShared HookResult()
                val message = args.getOrNull(1) as? String ?: return@hookShared HookResult()
                if (tag == "OutgoingMessageCoordinator" && message.contains("enqueue")) {
                    showBulletin("Raw hook: intercepted outgoing message pipeline")
                }
                HookResult()
            },
        )
    }

    override fun onMenuItemClick(key: String) {
        when (key) {
            MENU_SETTINGS_DEMO -> showBulletin("Injected settings row clicked")
            MENU_MESSAGE_DEMO -> showBulletin("Injected message menu clicked")
        }
    }

    override fun createSettings(): List<PluginSetting> = listOf(
        PluginSetting.Header("Hello World"),
        PluginSetting.Input(
            key = "template",
            text = "Greeting template",
            default = "Hello, {name}!",
            subtext = "Use {name} for the entered name",
        ),
        PluginSetting.Text(
            text = "Send .hello Alice in a chat",
            subtext = "Example: .hello Alice -> Hello, Alice!",
        ),
    )

    private fun registerRawHooks() {
        hookRawMethod(
            className = "ru.fromchat.ui.main.MainScreenKt",
            methodName = "selectMainPage",
            paramTypeNames = arrayOf(
                "int",
                "com.pr0gramm3r101.utils.WindowWidthSizeClass",
                "kotlinx.coroutines.CoroutineScope",
                "androidx.compose.foundation.pager.PagerState",
                "androidx.navigation.NavController",
            ),
            before = { args ->
                val page = (args.getOrNull(0) as? Number)?.toInt() ?: return@hookRawMethod HookResult()
                if (page == MAIN_PAGE_CONTACTS) {
                    if (!contactsNavBlockedNotified) {
                        contactsNavBlockedNotified = true
                        showBulletin("Raw hook: blocked Contacts tab navigation")
                    }
                    HookResult(strategy = HookStrategy.CANCEL)
                } else {
                    HookResult()
                }
            },
        )
        hookRawMethod(
            className = "ru.fromchat.ui.main.ContactsTabKt",
            methodName = "ContactsTab",
            paramTypeNames = arrayOf(
                "androidx.compose.runtime.Composer",
                "int",
            ),
            before = { _ ->
                HookResult(strategy = HookStrategy.CANCEL)
            },
        )
        hookRawMethod(
            className = "ru.fromchat.ui.main.MainScreenKt",
            methodName = "MainScreen",
            paramTypeNames = arrayOf(
                "androidx.compose.animation.SharedTransitionScope",
                "androidx.compose.animation.AnimatedVisibilityScope",
                "androidx.compose.material3.SnackbarHostState",
                "boolean",
                "int",
                "boolean",
                "kotlin.jvm.functions.Function1",
                "androidx.compose.runtime.Composer",
                "int",
                "int",
            ),
            before = { _ ->
                mainScreenNavItemOrdinal = 0
                HookResult()
            },
        )
        hookRawMethod(
            className = "androidx.compose.material3.NavigationBarKt",
            methodName = "NavigationBarItem",
            paramTypeNames = arrayOf(
                "androidx.compose.foundation.layout.RowScope",
                "boolean",
                "kotlin.jvm.functions.Function0",
                "kotlin.jvm.functions.Function2",
                "androidx.compose.ui.Modifier",
                "boolean",
                "kotlin.jvm.functions.Function2",
                "boolean",
                "androidx.compose.material3.NavigationBarItemColors",
                "androidx.compose.foundation.interaction.MutableInteractionSource",
                "androidx.compose.runtime.Composer",
                "int",
                "int",
            ),
            before = { _ ->
                mainScreenNavItemOrdinal += 1
                if (mainScreenNavItemOrdinal == CONTACTS_NAV_ITEM_ORDINAL) {
                    HookResult(strategy = HookStrategy.CANCEL)
                } else {
                    HookResult()
                }
            },
        )
    }

    private fun registerUiOverrides() {
        registerFeatureOverride(FEATURE_SETTINGS_DEVICES) { false }
        registerFeatureOverride(FEATURE_SETTINGS_LOGS) { false }
        registerFeatureOverride(FEATURE_CHAT_EMOJI) { false }
    }

    private fun registerInjectedUi() {
        registerUiOverlay(
            slot = OVERLAY_CHAT_BANNER,
            title = "Hello World plugin UI",
            message = "This banner was injected by the plugin engine.",
        )
        addMenuItem(
            MenuItemData(
                menuType = MenuItemType.SETTINGS_ROW,
                text = "Hello World demo action",
                subtext = "Injected settings row from plugin",
                priority = 10,
                onClickKey = MENU_SETTINGS_DEMO,
            ),
        )
        addMenuItem(
            MenuItemData(
                menuType = MenuItemType.MESSAGE_CONTEXT,
                text = "Hello World message action",
                subtext = "Injected message menu item",
                priority = 10,
                onClickKey = MENU_MESSAGE_DEMO,
            ),
        )
    }

    private fun onSendMessage(context: SendMessageHookContext): HookResult<SendMessageHookContext> {
        val raw = context.text.trim()
        if (!raw.startsWith(".hello")) return HookResult()
        val parts = raw.split(" ", limit = 2)
        val name = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "World" }
        val template = getSettingString("template", "Hello, {name}!")
        context.text = template.replace("{name}", name)
        showBulletin("High-level hook: rewrote .hello command")
        return HookResult(strategy = HookStrategy.MODIFY, value = context)
    }

    private companion object {
        const val MAIN_PAGE_CONTACTS = 1
        const val CONTACTS_NAV_ITEM_ORDINAL = 2
        const val FEATURE_SETTINGS_DEVICES = "settings.devices"
        const val FEATURE_SETTINGS_LOGS = "settings.logs"
        const val FEATURE_CHAT_EMOJI = "chat.emoji_picker"
        const val OVERLAY_CHAT_BANNER = "chat.banner"
        const val MENU_SETTINGS_DEMO = "hello_world.settings_demo"
        const val MENU_MESSAGE_DEMO = "hello_world.message_demo"

        @Volatile
        var mainScreenNavItemOrdinal: Int = 0

        @Volatile
        var contactsNavBlockedNotified: Boolean = false
    }
}
