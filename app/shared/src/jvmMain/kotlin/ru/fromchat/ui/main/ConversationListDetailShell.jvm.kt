package ru.fromchat.ui.main

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun listDetailPaneResizePointerIcon(): PointerIcon =
    PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
