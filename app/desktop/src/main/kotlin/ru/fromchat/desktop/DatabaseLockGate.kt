package ru.fromchat.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fromchat.Logger
import ru.fromchat.api.local.db.store.MessageDatabasePaths
import kotlin.time.Duration.Companion.milliseconds

object DatabaseLockGate {
    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    private val _processes = MutableStateFlow<List<LockingProcessInfo>>(emptyList())
    val processes: StateFlow<List<LockingProcessInfo>> = _processes.asStateFlow()

    private var monitorJob: Job? = null

    fun startMonitoring(scope: CoroutineScope, onUnlocked: () -> Unit) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var everBlocked = false
            var bootstrapDelivered = false
            while (isActive) {
                val locked = MessageDatabasePaths.isDatabaseLocked()
                if (locked) {
                    everBlocked = true
                    _blocked.value = true
                    _processes.value = DatabaseLockingProcessResolver.findLockingProcesses(
                        MessageDatabasePaths.lockTargetFiles(),
                    )
                } else {
                    if (_blocked.value) {
                        Logger.i("DatabaseLockGate", "database unlocked; resuming startup")
                    }
                    _blocked.value = false
                    _processes.value = emptyList()
                    if (!bootstrapDelivered) {
                        bootstrapDelivered = true
                        onUnlocked()
                    }
                    if (!everBlocked) return@launch
                    if (!_blocked.value) return@launch
                }
                delay(750.milliseconds)
            }
        }
    }

    fun killProcess(pid: Long): Boolean = DatabaseLockingProcessResolver.killProcess(pid)
}
