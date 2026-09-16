package ru.fromchat.plugins.sample.hello

import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.HookResult
import ru.fromchat.plugins.HookStrategy
import ru.fromchat.plugins.PluginSetting
import ru.fromchat.plugins.SendMessageHookContext

class HelloWorldPlugin : BasePlugin() {
    override fun onPluginLoad() {
        log("Hello World plugin loaded")
        showBulletin("Hello World plugin is active")
        addOnSendMessageHook { context ->
            onSendMessage(context)
        }
        hookShared(
            hookId = "logger.debug",
            after = { args, _ ->
                val tag = args.getOrNull(0) as? String ?: return@hookShared HookResult()
                val message = args.getOrNull(1) as? String ?: return@hookShared HookResult()
                if (tag == "OutgoingMessageCoordinator" && message.contains("enqueue")) {
                    showBulletin("Raw hook: intercepted outgoing message pipeline")
                }
                HookResult()
            },
        )
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
}
