package ru.fromchat.api.local.cache

import ru.fromchat.api.local.download.LocalDecodedImageCache

/** Deletes the `cacheDir/fromchat/` tree (blobs, DB file on disk). Call after [ru.fromchat.api.db.MessageRepository.clearAllCache]. */
expect suspend fun wipeFromChatCacheDirectory()

/** Removes decrypted attachment blobs and partial download state outside `fromchat/`. */
expect suspend fun wipeAttachmentCacheDirectories()

/** Deletes `cacheDir/fromchat/instances/<instanceId>/` auxiliary files (export index, pending saves). */
expect suspend fun wipeInstanceAuxiliaryCacheDirectory(instanceId: String)

suspend fun wipeAllOnDiskAttachmentCaches() {
    wipeFromChatCacheDirectory()
    wipeAttachmentCacheDirectories()
    LocalDecodedImageCache.evictPrefix("img_")
}
