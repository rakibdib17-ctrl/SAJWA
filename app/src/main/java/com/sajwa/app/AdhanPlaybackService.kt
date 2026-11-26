package com.sajwa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayer = intent?.getStringExtra("prayer") ?: "Prayer"
        val channelId = "sajwa_adhan_playback"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Adhan playback", NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SAJWA · $prayer Adhan")
            .setContentText("Adhan is playing")
            .setOngoing(true)
            .build()
        startForeground(4201, notification)
        try {
            player?.release()
            player = MediaPlayer.create(this, R.raw.adhan)
            player?.setOnCompletionListener { stopSelf() }
            player?.setOnErrorListener { _, _, _ -> stopSelf(); true }
            player?.start()
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { player?.stopSafely(); player?.release(); player = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun MediaPlayer.stopSafely() { try { if (isPlaying) stop() } catch (_: Exception) {} }
}