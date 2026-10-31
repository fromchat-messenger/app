@file:Suppress("unused")

package ru.fromchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.send.installOutboxForegroundDrain
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap

object IosApplicationBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun launchOnApplicationStart() {
        if (started) return
        started = true
        installOutboxForegroundDrain()
        scope.launch {
            runCatching { ApiClient.loadPersistedData() }
            runCatching { AttachmentTransferBootstrap.runColdStart() }
        }
    }
}
