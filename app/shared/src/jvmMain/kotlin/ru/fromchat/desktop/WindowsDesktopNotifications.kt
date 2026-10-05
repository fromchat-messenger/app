package ru.fromchat.desktop

import ru.fromchat.AppBuildInfo
import ru.fromchat.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/** Windows 10+ toast notifications with the branded app logo override. */
object WindowsDesktopNotifications {
    private const val TAG = "WindowsDesktopNotifications"

    fun deliver(
        title: String,
        body: String,
        subtitle: String = "",
    ): Boolean {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return false
        val aumid = if (AppBuildInfo.isDebug) {
            "denis0001-dev.FromChat.Beta"
        } else {
            "denis0001-dev.FromChat"
        }
        val iconPath = DesktopNotificationIcon.materialize() ?: run {
            Logger.i(TAG, "notification icon unavailable")
            return false
        }
        val iconUri = "file:///${iconPath.replace('\\', '/')}"
        val safeTitle = escapeXml(title.ifBlank { "FromChat" })
        val line1 = escapeXml(subtitle.ifBlank { body })
        val line2 = if (subtitle.isNotBlank() && body.isNotBlank()) escapeXml(body) else ""

        val binding = buildString {
            append("<image placement=\"appLogoOverride\" hint-crop=\"circle\" src=\"$iconUri\"/>")
            append("<text>$safeTitle</text>")
            append("<text>$line1</text>")
            if (line2.isNotEmpty()) {
                append("<text>$line2</text>")
            }
        }

        val script = """
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
            ${'$'}xml = [Windows.Data.Xml.Dom.XmlDocument]::new()
            ${'$'}xml.LoadXml(@"
            <toast activationType="foreground">
              <visual>
                <binding template="ToastGeneric">
                  $binding
                </binding>
              </visual>
            </toast>
            "@)
            ${'$'}notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("$aumid")
            ${'$'}toast = [Windows.UI.Notifications.ToastNotification]::new(${'$'}xml)
            ${'$'}notifier.Show(${'$'}toast)
            """.trimIndent()

        return runCatching {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            )
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(8, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                Logger.w(TAG, "toast PowerShell timed out")
                return false
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val ok = process.exitValue() == 0
            if (!ok) {
                Logger.w(TAG, "toast failed exit=${process.exitValue()} out=$output")
            } else {
                Logger.i(TAG, "toast delivered titleLen=${title.length}")
            }
            ok
        }.onFailure {
            Logger.w(TAG, "toast failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
