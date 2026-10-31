package ru.fromchat.api.local.send

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import ru.fromchat.utils.UtilsLibrary
import org.jetbrains.compose.resources.getString
import ru.fromchat.Res
import ru.fromchat.notif_media_upload_channel_name
import ru.fromchat.notif_media_upload_percent
import ru.fromchat.notif_media_upload_progress
import ru.fromchat.notif_media_upload_text
import ru.fromchat.notif_media_upload_title

private const val CHANNEL_ID = "fromchat_media_upload"
private const val NOTIFICATION_ID = 0xFC10

object MediaUploadForegroundHelper {
    suspend fun foregroundInfo(
        context: Context = UtilsLibrary.context,
        percent: Int? = null,
        filename: String? = null,
    ): ForegroundInfo {
        val channelName = getString(Res.string.notif_media_upload_channel_name)
        val title = getString(Res.string.notif_media_upload_title)
        val defaultText = getString(Res.string.notif_media_upload_text)
        ensureChannel(context, channelName)
        val contentText = when {
            percent != null -> {
                val percentLabel = getString(
                    Res.string.notif_media_upload_percent,
                    percent.coerceIn(0, 100),
                )
                if (!filename.isNullOrBlank()) {
                    getString(Res.string.notif_media_upload_progress, percentLabel, filename)
                } else {
                    percentLabel
                }
            }
            else -> defaultText
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_PROGRESS)
        when (val p = percent?.coerceIn(0, 100)) {
            null -> builder.setProgress(100, 0, true)
            else -> builder.setProgress(100, p, false)
        }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }
}
