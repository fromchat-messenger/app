package ru.fromchat.plugins.integration

import ru.fromchat.plugins.host.PluginEngine
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual object PluginInstallSupport {
    actual fun installFromPicker(): Boolean {
        val dialog = FileDialog(null as Frame?, "Install .fcplugin", FileDialog.LOAD).apply {
            file = "*.fcplugin"
            isVisible = true
        }
        val selected = dialog.file ?: return false
        val directory = dialog.directory ?: return false
        val file = File(directory, selected)
        if (!file.exists() || file.extension.lowercase() != "fcplugin") return false
        return runCatching {
            PluginEngine.installFcPluginArchive(file)
            true
        }.getOrDefault(false)
    }
}
