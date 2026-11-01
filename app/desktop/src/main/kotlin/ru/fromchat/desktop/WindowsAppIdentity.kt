package ru.fromchat.desktop

import com.sun.jna.WString
import com.sun.jna.platform.win32.Shell32
import ru.fromchat.utils.files.ensurePortableLayoutInstanceId
import ru.fromchat.utils.files.isPortableDesktop

/** Taskbar / jump-list label (Compose defaults to \"FromChat desktop\"). */
internal fun applyWindowsPortableShellDisplayNameEarly() {
    if (!isWindowsOs() || !isPortableDesktop()) return
    System.setProperty("java.awt.application.name", portableShellDisplayName())
}

internal fun portableShellDisplayName(): String =
    when (portableRegistrationId()) {
        "FromChat Beta" -> "FromChat Portable Beta"
        else -> "FromChat Portable"
    }

/** Taskbar / notification identity — must differ for portable vs installed so Windows does not activate the wrong exe. */
internal fun applyWindowsDesktopAppUserModelIdEarly() {
    if (!isWindowsOs()) return
    Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(resolveWindowsAppUserModelId()))
}

internal fun resolveWindowsAppUserModelId(): String {
    if (isPortableDesktop()) {
        val instance = ensurePortableLayoutInstanceId()
        if (instance != null) {
            return portableAppUserModelId(portableRegistrationId(), instance)
        }
    }
    System.getProperty("fromchat.windows.app.user.model.id")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return installedAppUserModelId(portableRegistrationId())
}

private fun portableRegistrationId(): String =
    when (System.getProperty("fromchat.app.data.name")?.trim()) {
        "FromChatBeta", "FromChat Beta" -> "FromChat Beta"
        else -> "FromChat"
    }

private fun portableAppUserModelId(registrationId: String, instanceId: String): String {
    val base = when (registrationId) {
        "FromChat Beta" -> "denis0001-dev.FromChat.Beta.Portable"
        else -> "denis0001-dev.FromChat.Portable"
    }
    return "$base.$instanceId"
}

private fun installedAppUserModelId(registrationId: String): String =
    when (registrationId) {
        "FromChat Beta" -> "denis0001-dev.FromChat.Beta"
        else -> "denis0001-dev.FromChat"
    }
