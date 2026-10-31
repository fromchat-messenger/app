package ru.fromchat.api.sync

actual suspend fun syncPushTokenAfterStartup() {
    // iOS: no APNs / FCM push token sync in this build.
}
