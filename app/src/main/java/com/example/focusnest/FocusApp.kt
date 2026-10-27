package com.example.focusnest

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class FocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Focus timer", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Session alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        const val CHANNEL_ONGOING = "focus_ongoing"
        const val CHANNEL_ALERT = "focus_alert"
    }
}
