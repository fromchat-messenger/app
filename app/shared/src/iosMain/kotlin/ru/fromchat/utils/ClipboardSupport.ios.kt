package ru.fromchat.utils

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

actual fun Clipboard.toSupport(): SupportClipboardManager {
    return object : SupportClipboardManager {
        private var listener: ((String) -> Unit)? = null

        override suspend fun setText(string: String) {
            UIPasteboard.generalPasteboard.string = string
            listener?.invoke(string)
        }

        override suspend fun getText(): String? =
            UIPasteboard.generalPasteboard.string

        override fun setTextListener(listener: (String) -> Unit) {
            this.listener = listener
        }
    }
}
