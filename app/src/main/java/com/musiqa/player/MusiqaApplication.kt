package com.musiqa.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MusiqaApplication : Application() {
    companion object {
        const val PLAYBACK_CHANNEL_ID = "musiqa_playback_v2"
        const val OVERLAY_CHANNEL_ID = "musiqa_overlay_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Controls and information for currently playing music"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val overlayChannel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "Floating Lyrics Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status for desktop floating lyrics"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playbackChannel)
            manager.createNotificationChannel(overlayChannel)
        }
    }
}
