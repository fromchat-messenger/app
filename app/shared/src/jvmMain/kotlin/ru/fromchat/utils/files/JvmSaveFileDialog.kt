package ru.fromchat.utils.files

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import ru.fromchat.ui.theme.applyMacOsAppAppearance
import ru.fromchat.ui.theme.jvmPlatformSystemDarkTheme

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/** Native save panel on macOS; [JFileChooser] elsewhere. */
fun showJvmSaveFileDialog(
    defaultFilename: String,
    owner: Window? = null,
    title: String = "Save",
): File? {
    if (isMacOs()) {
        applyMacOsAppAppearance(jvmPlatformSystemDarkTheme())
        val dialog = when (owner) {
            is Frame -> FileDialog(owner, title, FileDialog.SAVE)
            else -> FileDialog(null as Frame?, title, FileDialog.SAVE)
        }.apply {
            file = defaultFilename
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }
    val chooser = JFileChooser().apply {
        dialogType = JFileChooser.SAVE_DIALOG
        selectedFile = File(defaultFilename)
    }
    if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile
}
