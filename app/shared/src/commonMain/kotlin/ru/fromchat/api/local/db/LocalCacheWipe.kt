package ru.fromchat.api.local.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.PublicChatProfileSync
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.cache.PendingFileSaveRegistry
import ru.fromchat.api.local.cache.wipeAttachmentCacheDirectories
import ru.fromchat.api.local.cache.wipeFromChatCacheDirectory
import ru.fromchat.api.local.cache.wipeInstanceAuxiliaryCacheDirectory
import ru.fromchat.api.local.db.store.InstanceRegistryStore
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.local.download.DownloadedFileRegistry
import ru.fromchat.api.local.download.LocalDecodedImageCache
import ru.fromchat.api.local.send.cancelOutboxProcessing
import ru.fromchat.ui.chat.panels.dm.DmPanelCache
import ru.fromchat.ui.chat.utils.PublicChatPanelCache

/**
 * Drops the on-disk FromChat cache tree and reopens SQLite on next access.
 * Call [ru.fromchat.api.local.cache.writeFromChatCacheGeneration] after this when wiping from settings.
 */
suspend fun wipeLocalCacheOnDisk() {
    val instanceId = runCatching { CacheContext.activeInstanceId.value.trim() }.getOrDefault("")
    if (instanceId.isNotEmpty()) {
        cancelOutboxProcessing(instanceId)
    }
    withContext(Dispatchers.Default) {
        MessageDatabaseProvider.closeAndReset()
    }
    wipeFromChatCacheDirectory()
    wipeAttachmentCacheDirectories()
    clearInMemoryAccountCaches()
}

/**
 * Clears all per-account cache for the active server instance on logout.
 * Preserves other server-instance partitions in SQLite for multi-server use.
 */
suspend fun clearAccountCacheOnLogout(instanceId: String) {
    val id = instanceId.trim()
    PublicChatProfileSync.resetOnLogout()
    if (id.isNotEmpty()) {
        cancelOutboxProcessing(id)
        runCatching { MessageRepository.purgeAllPendingForInstance() }
        runCatching { AttachmentDownloadScheduler.cancelAllOnLogout() }
        runCatching { InstanceRegistryStore.purgePartition(id) }
        runCatching { InstanceRegistryStore.clearServerBindingForCurrentConfig() }
        runCatching { wipeInstanceAuxiliaryCacheDirectory(id) }
        runCatching { DownloadedFileRegistry.clearForInstance(id) }
        runCatching { PendingFileSaveRegistry.clearForInstance(id) }
    }
    runCatching { ApiClient.clearAllDownloadCachesOnLogout() }
    wipeAttachmentCacheDirectories()
    clearInMemoryAccountCaches()
}

private suspend fun clearInMemoryAccountCaches() {
    runCatching { ProfileCache.clear() }
    runCatching { PublicChatProfileCache.clear() }
    runCatching { DmPanelCache.clearAll() }
    PublicChatPanelCache.clear()
    AttachmentDownloadNotifier.resetOnLogout()
    DecryptedImageCache.clearMemoryCache()
    DecryptedFileCache.clearMemoryCache()
    MessageRepository.resetListPreviewStringsOnLogout()
    LocalDecodedImageCache.evictPrefix("img_")
}
