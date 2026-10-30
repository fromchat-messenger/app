package ru.fromchat.ui.chat.utils

import android.widget.Toast
import ru.fromchat.utils.UtilsLibrary

internal actual fun showAttachmentOpenFailed(message: String) {
    Toast.makeText(UtilsLibrary.context, message, Toast.LENGTH_SHORT).show()
}
