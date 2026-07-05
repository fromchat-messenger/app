package ru.fromchat.api.local.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.local.download.AttachmentDownloadScheduler.MAX_PARALLEL
import ru.fromchat.ui.chat.utils.AttachmentDownloadVisibility
import ru.fromchat.api.local.AttachmentMediaLog

/**
 * Limits concurrent DM attachment decrypt/download work to [MAX_PARALLEL] across **different** keys.
 * The same [storageKey] never runs more than one download at a time; duplicate callers share one result.
 */
object AttachmentDownloadScheduler {
    private const val MAX_PARALLEL = 2

    init {
        AttachmentDownloadNotifier.bindInFlightCheck { storageKey -> isActive(storageKey) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val runMutexByKey = mutableMapOf<String, Mutex>()

    private data class Pending(
        val storageKey: String,
        val messageId: Int,
        val enqueuedAt: Long,
        val work: suspend () -> String?,
        val result: CompletableDeferred<String?>,
        val keepAliveInBackground: Boolean,
    )

    fun isActive(storageKey: String): Boolean =
        activeJobs[storageKey]?.isActive == true

    private val waiting = mutableListOf<Pending>()
    private val keyToDeferred = mutableMapOf<String, CompletableDeferred<String?>>()
    private val activeJobs = mutableMapOf<String, Job>()
    private var activeCount = 0

    private fun runMutexFor(storageKey: String): Mutex =
        runMutexByKey.getOrPut(storageKey) { Mutex() }

    /**
     * Cancels queued and in-flight work for [storageKey]. Different keys are unaffected.
     */
    suspend fun cancel(storageKey: String) {
        runMutexFor(storageKey).withLock {
            val job = mutex.withLock {
                waiting.removeAll { it.storageKey == storageKey }
                keyToDeferred[storageKey]?.let { deferred ->
                    if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                }
                activeJobs.remove(storageKey)
            }
            job?.cancelAndJoin()
            mutex.withLock {
                keyToDeferred.remove(storageKey)
            }
        }
    }

    /**
     * Runs [work] when a global slot is available. Duplicate [storageKey] shares one result and one job.
     */
    suspend fun run(
        storageKey: String,
        messageId: Int,
        work: suspend () -> String?,
        keepAliveInBackground: Boolean = false,
    ): String? = runMutexFor(storageKey).withLock {
        val existing = mutex.withLock {
            keyToDeferred[storageKey]?.takeIf { !it.isCompleted }
        }
        if (existing != null) {
            return existing.await()
        }

        mutex.withLock { activeJobs[storageKey] }
            ?.takeIf { it.isActive }
            ?.cancelAndJoin()

        val deferred = mutex.withLock {
            val created = CompletableDeferred<String?>()
            keyToDeferred[storageKey] = created
            waiting.removeAll { it.storageKey == storageKey }
            waiting.add(
                Pending(
                    storageKey = storageKey,
                    messageId = messageId,
                    enqueuedAt = AttachmentMediaLog.nowMs(),
                    work = work,
                    result = created,
                    keepAliveInBackground = keepAliveInBackground,
                ),
            )
            sortWaitingLocked()
            created
        }
        pumpLocked()
        deferred.await()
    }

    fun reprioritize() {
        scope.launch {
            mutex.withLock { sortWaitingLocked() }
            pumpLocked()
        }
    }

    private suspend fun pumpLocked() {
        val toStart = mutex.withLock {
            val jobs = mutableListOf<Pending>()
            while (activeCount < MAX_PARALLEL && waiting.isNotEmpty()) {
                val next = waiting.first()
                if (activeJobs[next.storageKey]?.isActive == true) {
                    break
                }
                waiting.removeAt(0)
                activeCount++
                jobs.add(next)
            }
            jobs
        }
        for (pending in toStart) {
            val job = scope.launch {
                try {
                    runPending(pending)
                } finally {
                    mutex.withLock {
                        activeJobs.remove(pending.storageKey)
                    }
                }
            }
            mutex.withLock {
                activeJobs[pending.storageKey] = job
            }
        }
    }

    private suspend fun runPending(pending: Pending) {
        if (pending.keepAliveInBackground) {
            AttachmentDownloadForeground.onFileDownloadStarted(pending.storageKey)
        }
        try {
            val outcome = pending.work()
            mutex.withLock {
                if (!pending.result.isCompleted) {
                    pending.result.complete(outcome)
                }
                if (keyToDeferred[pending.storageKey] === pending.result) {
                    keyToDeferred.remove(pending.storageKey)
                }
            }
        } catch (error: CancellationException) {
            mutex.withLock {
                if (!pending.result.isCompleted) {
                    pending.result.complete(null)
                }
                if (keyToDeferred[pending.storageKey] === pending.result) {
                    keyToDeferred.remove(pending.storageKey)
                }
            }
            throw error
        } catch (error: Throwable) {
            mutex.withLock {
                if (!pending.result.isCompleted) {
                    pending.result.completeExceptionally(error)
                }
                if (keyToDeferred[pending.storageKey] === pending.result) {
                    keyToDeferred.remove(pending.storageKey)
                }
            }
            throw error
        } finally {
            if (pending.keepAliveInBackground) {
                AttachmentDownloadForeground.onFileDownloadFinished(pending.storageKey)
            }
            mutex.withLock {
                activeCount = (activeCount - 1).coerceAtLeast(0)
            }
            pumpLocked()
        }
    }

    private fun sortWaitingLocked() {
        waiting.sortWith(
            compareBy<Pending> { pending ->
                if (AttachmentDownloadVisibility.isPrioritized(pending.messageId)) 0 else 1
            }.thenBy { it.enqueuedAt },
        )
    }

    suspend fun cancelAllOnLogout() {
        val keys = mutex.withLock {
            (activeJobs.keys + waiting.map { it.storageKey } + keyToDeferred.keys).toSet()
        }
        keys.forEach { cancel(it) }
    }
}

internal fun checkAttachmentDownloadActive(storageKey: String) {
    if (AttachmentDownloadNotifier.isCancelled(storageKey)) {
        throw CancellationException("attachment download cancelled")
    }
}

/** Cooperative cancel check for in-flight decrypt/download loops. */
internal suspend fun ensureAttachmentDownloadActive(storageKey: String) {
    currentCoroutineContext().ensureActive()
    checkAttachmentDownloadActive(storageKey)
}
