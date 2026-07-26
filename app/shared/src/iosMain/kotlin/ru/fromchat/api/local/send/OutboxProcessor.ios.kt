package ru.fromchat.api.local.send

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import ru.fromchat.api.local.cache.CacheContext

private var foregroundDrainInstalled = false

/** Install once: drain outbox when the app becomes active (parity with WorkManager). */
fun installOutboxForegroundDrain() {
    if (foregroundDrainInstalled) return
    foregroundDrainInstalled = true
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isNotEmpty()) {
            scheduleOutboxProcessing(instanceId)
        }
    }
}

/** iOS: drain outbox on enqueue and when the app returns to foreground. */
actual fun scheduleOutboxProcessing(instanceId: String) {
    if (instanceId.trim().isEmpty()) return
    MainScope().launch {
        OutgoingMessageCoordinator.drainActiveInstanceOutbox()
    }
}

actual fun cancelOutboxProcessing(instanceId: String) = Unit
