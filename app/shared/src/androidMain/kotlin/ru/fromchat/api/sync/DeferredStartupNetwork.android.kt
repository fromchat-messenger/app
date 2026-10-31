package ru.fromchat.api.sync

import ru.fromchat.api.push.ensureFcmTokenRegistered
import ru.fromchat.api.push.uploadPendingFcmTokenIfAvailable

actual suspend fun syncPushTokenAfterStartup() {
    uploadPendingFcmTokenIfAvailable()
    ensureFcmTokenRegistered()
}
