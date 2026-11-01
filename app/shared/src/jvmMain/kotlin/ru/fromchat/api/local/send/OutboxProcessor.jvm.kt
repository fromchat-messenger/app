package ru.fromchat.api.local.send

actual fun scheduleOutboxProcessing(instanceId: String) {
    OutgoingMessageCoordinator.runOutboxDrain(instanceId.trim())
}

actual fun cancelOutboxProcessing(instanceId: String) = Unit
