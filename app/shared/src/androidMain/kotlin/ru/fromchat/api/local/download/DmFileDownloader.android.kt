package ru.fromchat.api.local.download

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import ru.fromchat.utils.UtilsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.logging.Logger
import ru.fromchat.api.local.cache.AttachmentFileProvider
import ru.fromchat.api.local.mimeTypeForFilename
import ru.fromchat.ui.chat.uriToLocalCacheFile

actual suspend fun openCachedAttachmentFile(
    cacheUri: String,
    mimeType: String,
    displayFilename: String?,
): Boolean = withContext(Dispatchers.Main) {
    val tag = "AttachmentOpen"
    val appContext = UtilsLibrary.context
    val context = findActivity(appContext) ?: appContext
    val file = uriToLocalCacheFile(cacheUri) ?: return@withContext false
    if (!file.exists() || file.length() <= 0L) return@withContext false
    val contentUri = AttachmentFileProvider.uriForFile(appContext, file) ?: return@withContext false
    val nameForMime = displayFilename?.takeIf { it.isNotBlank() }
        ?: AttachmentFileProvider.displayNameFor(file)
    val resolvedMime = mimeType.takeIf { it.isNotBlank() && mimeType != "application/octet-stream" }
        ?: mimeTypeForFilename(nameForMime)

    runCatching {
        fun commonFlags(intent: Intent): Intent = intent.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri(nameForMime, contentUri)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun buildViewIntent(type: String): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, type)
            }.let(::commonFlags)

        fun queryHandlerPackages(pm: PackageManager, intent: Intent): List<String> {
            val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            return infos.mapNotNull { it.activityInfo?.packageName }.distinct()
        }

        fun ensureGrantForIntent(intent: Intent): Boolean {
            val pm = context.packageManager
            val pkgs = queryHandlerPackages(pm, intent)
            if (pkgs.isEmpty()) return false
            pkgs.forEach { pkg ->
                runCatching {
                    context.grantUriPermission(
                        pkg,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            return true
        }

        // IMPORTANT: do NOT wrap in Intent.createChooser(...) here.
        // Starting the raw ACTION_VIEW intent allows Android to:
        // - open the default app when a default is set
        // - show the system resolver with "Once/Always" when multiple apps can handle it
        val primary = buildViewIntent(resolvedMime)
        val fallback = buildViewIntent("*/*")
        val hasPrimary = ensureGrantForIntent(primary)
        val hasFallback = hasPrimary || ensureGrantForIntent(fallback)
        if (!hasPrimary && !hasFallback) {
            Logger.w(tag, "No handler for uri=$contentUri mime=$resolvedMime name=$nameForMime")
            return@runCatching false
        }

        try {
            context.startActivity(primary)
            Logger.i(tag, "startActivity ok mime=$resolvedMime name=$nameForMime")
        } catch (t: Throwable) {
            Logger.w(tag, "startActivity primary failed, falling back mime=$resolvedMime uri=$contentUri", t)
            context.startActivity(fallback)
        }
        true
    }.onFailure { t ->
        Logger.e(tag, "openCachedAttachmentFile failed cacheUri=$cacheUri", t)
    }.getOrDefault(false)
}

private tailrec fun findActivity(ctx: Context?): Activity? = when (ctx) {
    is Activity -> ctx
    is ContextWrapper -> findActivity(ctx.baseContext)
    else -> null
}
