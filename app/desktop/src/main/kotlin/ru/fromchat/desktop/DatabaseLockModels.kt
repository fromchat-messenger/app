package ru.fromchat.desktop

import androidx.compose.ui.graphics.ImageBitmap

data class LockingProcessInfo(
    val pid: Long,
    val name: String,
    val description: String?,
    val executablePath: String?,
    val icon: ImageBitmap?,
)
