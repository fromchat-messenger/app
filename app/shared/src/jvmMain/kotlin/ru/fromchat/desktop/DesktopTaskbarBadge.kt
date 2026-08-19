package ru.fromchat.desktop

import java.awt.Taskbar
import ru.fromchat.Logger

/** Best-effort dock / taskbar unread badge (macOS when supported; no-op elsewhere). */
object DesktopTaskbarBadge {
    private const val TAG = "DesktopTaskbarBadge"

    fun setUnreadCount(count: Int) {
        runCatching {
            if (!Taskbar.isTaskbarSupported()) return
            val taskbar = Taskbar.getTaskbar()
            val badgeFeature = runCatching {
                Taskbar.Feature.valueOf("ICON_BADGE")
            }.getOrNull() ?: return
            if (!taskbar.isSupported(badgeFeature)) return
            val badge = when {
                count <= 0 -> null
                count > 99 -> "99+"
                else -> count.toString()
            }
            val method = taskbar.javaClass.getMethod("setIconBadge", String::class.java)
            method.invoke(taskbar, badge)
        }.onFailure {
            Logger.d(TAG, "icon badge update failed: ${it.message}")
        }
    }
}
