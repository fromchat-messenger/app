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

    private val _runtimeSqliteBusy = MutableStateFlow(false)

    private var monitorJob: Job? = null

    fun onSqliteBusy(throwable: Throwable?): Boolean {
        Logger.w("DatabaseLockGate", "SQLite busy", throwable)
        _runtimeSqliteBusy.value = true
        return refreshBlockedState()
    }

    fun startMonitoring(scope: CoroutineScope, onUnlocked: () -> Unit) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var bootstrapDelivered = false
            while (isActive) {
                val blocked = refreshBlockedState()
                if (!blocked && !bootstrapDelivered) {
                    bootstrapDelivered = true
                    onUnlocked()
                }
                if (!blocked && bootstrapDelivered) return@launch
                delay(750.milliseconds)
            }
        }
    }

    fun killProcess(pid: Long): Boolean = DatabaseLockingProcessResolver.killProcess(pid)

    fun releaseRuntimeLock() {
        MessageDatabaseRuntimeLock.release()
    }

    private fun refreshBlockedState(): Boolean {
        val externalProcesses = DatabaseLockingProcessResolver.findLockingProcesses(
            MessageDatabasePaths.lockProbeFiles(),
        )

        if (!MessageDatabaseRuntimeLock.isHeld() && !MessageDatabaseRuntimeLock.tryAcquire()) {
            Logger.w(
                "DatabaseLockGate",
                "Instance lock held by another process; blockers=$externalProcesses",
            )
            _blocked.value = true
            _processes.value = externalProcesses
            return true
        }

        if (externalProcesses.isNotEmpty()) {
            Logger.w(
                "DatabaseLockGate",
                "Database files in use by other process(es): $externalProcesses",
            )
            _blocked.value = true
            _processes.value = externalProcesses
            return true
        }

        if (_runtimeSqliteBusy.value) {
            Logger.i(
                "DatabaseLockGate",
                "SQLite busy with no external locker — treating as in-process contention, not blocking",
            )
            _runtimeSqliteBusy.value = false
        }

        _blocked.value = false
        _processes.value = emptyList()
        return false
    }
}
