package ru.fromchat.incognito

import ru.fromchat.utils.files.AppDataRoot

internal actual fun defaultAppDataRootPath(): String =
    AppDataRoot.resolve().absolutePath
