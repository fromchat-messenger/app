package ru.fromchat.api.local.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import ru.fromchat.Res
import ru.fromchat.notif_file_copy_channel_name
import ru.fromchat.notif_file_copy_text
import ru.fromchat.notif_file_copy_title

/**
 * Foreground service for copying decrypted attachments to a user-chosen destination (SAF).
 */
class AttachmentFileCopyForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START, null -> startIfNeeded(intent)
        }
        return START_STICKY
    }

    private fun startIfNeeded(intent: Intent?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME)
            ?: runBlocking { getString(Res.string.notif_file_copy_channel_name) }
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: runBlocking { getString(Res.string.notif_file_copy_title) }
        val defaultText = intent?.getStringExtra(EXTRA_DEFAULT_TEXT)
            ?: runBlocking { getString(Res.string.notif_file_copy_text) }
        ensureChannel(nm, channelName)
        val label = intent?.getStringExtra(EXTRA_LABEL)
        val contentText = label?.takeIf { it.isNotBlank() } ?: defaultText
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(nm: NotificationManager, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "fromchat_file_copy"
        private const val NOTIFICATION_ID = 0xFC13

        private const val ACTION_START =
            "ru.fromchat.api.local.workers.AttachmentFileCopyForegroundService.START"
        private const val ACTION_STOP =
            "ru.fromchat.api.local.workers.AttachmentFileCopyForegroundService.STOP"

        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DEFAULT_TEXT = "default_text"
        private const val EXTRA_LABEL = "label"

        private val activeKeys = mutableSetOf<String>()

        @Synchronized
        fun onJobStarted(app: Context, storageKey: String, displayLabel: String? = null) {
            val wasEmpty = activeKeys.isEmpty()
            activeKeys.add(storageKey)
            if (!wasEmpty) return
            val intent = Intent(app, AttachmentFileCopyForegroundService::class.java).apply {
                action = ACTION_START
                displayLabel?.let { putExtra(EXTRA_LABEL, it) }
            }
            ContextCompat.startForegroundService(app, intent)
        }

        @Synchronized
        fun onJobFinished(app: Context, storageKey: String) {
            activeKeys.remove(storageKey)
            if (activeKeys.isNotEmpty()) return
            val intent = Intent(app, AttachmentFileCopyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            app.startService(intent)
        }
    }
}
