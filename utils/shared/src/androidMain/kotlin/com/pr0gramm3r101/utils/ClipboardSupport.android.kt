package com.pr0gramm3r101.utils

import android.content.ClipData as AndroidClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual fun Clipboard.toSupport(): SupportClipboardManager {
    val clipboard = this
    return object : SupportClipboardManager {
        private var listener: ((String) -> Unit)? = null

        override suspend fun setText(string: String) {
            val clipData = AndroidClipData.newPlainText("text", string)
            clipboard.setClipEntry(ClipEntry(clipData))
            listener?.invoke(string)
        }

        override suspend fun getText(): String? {
            val entry = clipboard.getClipEntry() ?: return null
            return entry.clipData.getItemAt(0)?.text?.toString()
        }

        override fun setTextListener(listener: (String) -> Unit) {
            this.listener = listener
        }
    }
}
