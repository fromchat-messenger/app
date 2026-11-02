package ru.fromchat.incognito

import ru.fromchat.utils.UtilsLibrary

internal actual fun defaultAppDataRootPath(): String =
    UtilsLibrary.context.noBackupFilesDir.absolutePath
