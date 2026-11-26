package com.sajwa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayer = intent?.getStringExtra("prayer") ?: "Prayer"
        val channelId = "sajwa_adhan_playback"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "SAJWA Adhan playback", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    description = "Shown while SAJWA is playing the Adhan"
                }
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SAJWA · $prayer Adhan")
            .setContentText("Playing Adhan using your device alarm volume")
            .setOngoing(true)
            .build()
        startForeground(4201, notification)

        try {
            player?.release()
            val localFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "sajwa_adhan.mp3")
            val uri = if (localFile.exists() && localFile.length() > 0) Uri.fromFile(localFile)
                else intent?.getStringExtra("audio_uri")?.let { Uri.parse(it) }
                ?: Uri.parse("android.resource://$packageName/${R.raw.adhan}")

            player = MediaPlayer().apply {
                // USAGE_ALARM routes playback to the system alarm-volume stream,
                // independently of the media/music volume slider.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                setDataSource(this@AdhanPlaybackService, uri)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) { }
            it.release()
        }
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
