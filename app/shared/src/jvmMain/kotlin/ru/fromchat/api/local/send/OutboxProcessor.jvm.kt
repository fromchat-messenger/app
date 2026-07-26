package ru.fromchat.api.local.send

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

actual fun scheduleOutboxProcessing(instanceId: String) {
    if (instanceId.trim().isEmpty()) return
    MainScope().launch {
        OutgoingMessageCoordinator.drainActiveInstanceOutbox()
    }
}

actual fun cancelOutboxProcessing(instanceId: String) = Unit
