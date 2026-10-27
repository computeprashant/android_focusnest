package com.example.focusnest

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the timer and sound running with the screen off or the app in the background.
 * It shows the ongoing notification while a session runs and stops when the timer pauses.
 */
class FocusService : Service(), TimerEngine.Listener {

    override fun onCreate() {
        super.onCreate()
        TimerEngine.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onTimerChanged() {
        if (TimerEngine.running) {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        TimerEngine.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, FocusApp.CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("${TimerEngine.phase.label} · ${TimerEngine.subject}")
            .setContentText(formatTime(TimerEngine.remainingMs))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1
    }
}
