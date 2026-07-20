package ru.fromchat.ui.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ru.fromchat.api.calls.CallStore
import ru.fromchat.notifications.NotificationSmallIcon

/**
 * Foreground call session: keeps camera / mic eligible in background.
 *
 * Uses [NotificationCompat.CallStyle] for ongoing calls (API 31+) so the system treats this as an
 * active call (priority in shade, better integration with OEM “live” / call surfaces). Samsung
 * **Now Brief** is first‑party only; there is no public API for third‑party apps to plug into it.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> {
                CallStore.endCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> startAsForeground(intent)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground(startIntent: Intent) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelLabel = startIntent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Ongoing call"
        val peerName = startIntent.getStringExtra(EXTRA_PEER_NAME)?.trim().orEmpty().ifBlank {
            startIntent.getStringExtra(EXTRA_TITLE).orEmpty()
        }
        if (peerName.isBlank()) {
            stopSelf()
            return
        }
        val statusText = startIntent.getStringExtra(EXTRA_STATUS_TEXT).orEmpty()
        val startWallMs = startIntent.getLongExtra(EXTRA_START_WALL_MS, System.currentTimeMillis())

        ensureActiveCallChannel(nm, channelLabel)

        val hangUpPi = PendingIntent.getService(
            this,
            RC_HANG_UP,
            Intent(this, CallForegroundService::class.java).apply { action = ACTION_HANG_UP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPi = if (openApp != null) {
            PendingIntent.getActivity(
                this,
                RC_OPEN_APP,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }

        val caller = Person.Builder()
            .setName(peerName)
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotificationSmallIcon.resId(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(startWallMs)
            .setShowWhen(false)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setColorized(true)
            .setColor(Color.rgb(0, 107, 94))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
        if (contentPi != null) {
            builder.setContentIntent(contentPi)
        }
        if (statusText.isNotBlank()) {
            builder.setSubText(statusText)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    caller,
                    hangUpPi,
                ),
            )
        } else {
            builder.setContentTitle(peerName)
                .setContentText(statusText.ifBlank { channelLabel })
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Camera + mic only. Do not use MEDIA_PROJECTION here: that type needs
            // FOREGROUND_SERVICE_MEDIA_PROJECTION + capture privileges; screen share uses
            // Activity-scoped MediaProjection (LiveKit), not this service.
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureActiveCallChannel(nm: NotificationManager, label: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            nm.deleteNotificationChannel(CHANNEL_ID)
        }
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                label,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = label
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "fromchat_call_active"
        private const val LEGACY_CHANNEL_ID = "fromchat_call_ongoing"
        private const val NOTIFICATION_ID = 99101
        private const val RC_HANG_UP = 1002
        private const val RC_OPEN_APP = 1003

        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_START_WALL_MS = "extra_start_wall_ms"

        private const val ACTION_START = "ru.fromchat.calls.CallForegroundService.START"
        private const val ACTION_STOP = "ru.fromchat.calls.CallForegroundService.STOP"
        private const val ACTION_HANG_UP = "ru.fromchat.calls.CallForegroundService.HANG_UP"

        fun start(
            context: Context,
            channelName: String,
            peerDisplayName: String,
            title: String,
            statusText: String,
            callStartWallMs: Long,
        ) {
            val app = context.applicationContext
            val intent = Intent(app, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_PEER_NAME, peerDisplayName)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_STATUS_TEXT, statusText)
                putExtra(EXTRA_START_WALL_MS, callStartWallMs)
            }
            ContextCompat.startForegroundService(app, intent)
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.startService(
                Intent(app, CallForegroundService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
