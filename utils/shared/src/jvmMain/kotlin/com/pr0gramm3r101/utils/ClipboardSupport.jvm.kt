package com.pr0gramm3r101.utils

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun Clipboard.toSupport(): SupportClipboardManager {
    return object : SupportClipboardManager {
        private var listener: ((String) -> Unit)? = null

        override suspend fun setText(string: String) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(string), null)
            listener?.invoke(string)
        }

        override suspend fun getText(): String? {
            val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
            if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
            return contents.getTransferData(DataFlavor.stringFlavor) as? String
        }

        override fun setTextListener(listener: (String) -> Unit) {
            this.listener = listener
        }
    }
}
