package ru.fromchat.logging

import android.content.Intent
import androidx.core.content.FileProvider
import ru.fromchat.utils.UtilsLibrary
import java.io.File

actual object LogShare {
    actual fun shareText(title: String, text: String) {
        val context = UtilsLibrary.context
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    actual fun shareFile(title: String, filePath: String, mimeType: String) {
        val context = UtilsLibrary.context
        val file = File(filePath)
        if (!file.isFile) {
            shareText(title, "")
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.attachment_files",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
