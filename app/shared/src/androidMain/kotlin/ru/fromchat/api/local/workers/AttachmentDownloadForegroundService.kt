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
import ru.fromchat.notif_file_download_channel_name
import ru.fromchat.notif_file_download_percent
import ru.fromchat.notif_file_download_progress
import ru.fromchat.notif_file_download_text
import ru.fromchat.notif_file_download_title

/**
 * Foreground service for in-flight DM file attachment downloads (decrypt + cache).
 */
class AttachmentDownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE -> {
                val percent = intent.getIntExtra(EXTRA_PERCENT, -1)
                val label = intent.getStringExtra(EXTRA_LABEL)
                if (percent >= 0) {
                    updateNotification(percent, label)
                }
            }
            ACTION_START, null -> startIfNeeded(intent)
        }
        return START_STICKY
    }

    private fun startIfNeeded(intent: Intent?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME)
            ?: runBlocking { getString(Res.string.notif_file_download_channel_name) }
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: runBlocking { getString(Res.string.notif_file_download_title) }
        val defaultText = intent?.getStringExtra(EXTRA_DEFAULT_TEXT)
            ?: runBlocking { getString(Res.string.notif_file_download_text) }
        ensureChannel(nm, channelName)
        cachedTitle = title
        cachedDefaultText = defaultText
        val percent = intent?.getIntExtra(EXTRA_PERCENT, 1) ?: 1
        val label = intent?.getStringExtra(EXTRA_LABEL)
        val notification = buildNotification(title, defaultText, percent, label)
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

    private fun updateNotification(percent: Int, label: String?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val title = cachedTitle ?: return
        val defaultText = cachedDefaultText ?: return
        nm.notify(
            NOTIFICATION_ID,
            buildNotification(title, defaultText, percent.coerceIn(0, 100), label),
        )
    }

    private fun buildNotification(
        title: String,
        defaultText: String,
        percent: Int,
        label: String?,
    ): Notification {
        val contentText = runBlocking {
            val percentLabel = getString(
                Res.string.notif_file_download_percent,
                percent.coerceIn(0, 100),
            )
            if (!label.isNullOrBlank()) {
                getString(Res.string.notif_file_download_progress, percentLabel, label)
            } else {
                percentLabel
            }
        }.let { resolved ->
            if (percent > 0) resolved else defaultText
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_PROGRESS)
        when (val p = percent.coerceIn(0, 100)) {
            0 -> builder.setProgress(100, 0, true)
            else -> builder.setProgress(100, p, false)
        }
        return builder.build()
    }

    private fun ensureChannel(nm: NotificationManager, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "fromchat_file_download"
        private const val NOTIFICATION_ID = 0xFC12

        private const val ACTION_START = "ru.fromchat.api.local.workers.AttachmentDownloadForegroundService.START"
        private const val ACTION_STOP = "ru.fromchat.api.local.workers.AttachmentDownloadForegroundService.STOP"
        private const val ACTION_UPDATE = "ru.fromchat.api.local.workers.AttachmentDownloadForegroundService.UPDATE"

        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DEFAULT_TEXT = "default_text"
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_LABEL = "label"

        private val activeKeys = mutableSetOf<String>()
        private var cachedTitle: String? = null
        private var cachedDefaultText: String? = null
        private var lastPercent: Int = 0
        private var lastLabel: String? = null
        private var lastNotifUpdateMs: Long = 0L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L

        @Synchronized
        fun onJobStarted(app: Context, storageKey: String) {
            val wasEmpty = activeKeys.isEmpty()
            activeKeys.add(storageKey)
            if (!wasEmpty) return
            val intent = Intent(app, AttachmentDownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PERCENT, lastPercent.coerceAtLeast(1))
                lastLabel?.let { putExtra(EXTRA_LABEL, it) }
            }
            ContextCompat.startForegroundService(app, intent)
        }

        @Synchronized
        fun onJobFinished(app: Context, storageKey: String) {
            activeKeys.remove(storageKey)
            if (activeKeys.isNotEmpty()) return
            val intent = Intent(app, AttachmentDownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            app.startService(intent)
        }

        @Synchronized
        fun updateProgress(app: Context, percent: Int, displayLabel: String?) {
            val pct = percent.coerceIn(0, 100)
            val now = System.currentTimeMillis()
            val forceUpdate = pct !in 2..<100
            if (!forceUpdate && now - lastNotifUpdateMs < NOTIFICATION_MIN_INTERVAL_MS) {
                return
            }
            lastPercent = pct
            lastNotifUpdateMs = now
            if (!displayLabel.isNullOrBlank()) {
                lastLabel = displayLabel
            }
            if (activeKeys.isEmpty()) return
            val intent = Intent(app, AttachmentDownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PERCENT, lastPercent)
                lastLabel?.let { putExtra(EXTRA_LABEL, it) }
            }
            app.startService(intent)
        }
    }
}
