package com.sajwa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prayer = intent?.getStringExtra("prayer") ?: "Prayer"
        val channelId = "sajwa_adhan_playback"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "SAJWA Adhan playback", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    description = "SAJWA Adhan is playing. Use Stop Adhan to end playback."
                    enableLights(true)
                    lightColor = Color.rgb(217, 189, 130)
                }
            )
        }
        val stopPending = PendingIntent.getBroadcast(
            this, 4202, Intent(this, StopAdhanReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(Color.rgb(8, 43, 35))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle("SAJWA  ·  $prayer Adhan")
            .setContentText("Adhan is playing")
            .setSubText("Prayer time reminder")
            .setStyle(NotificationCompat.BigTextStyle().bigText("SAJWA is playing the Adhan for $prayer. Tap Stop Adhan below to stop playback immediately."))
            .addAction(android.R.drawable.ic_media_pause, "STOP ADHAN", stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        try {
            player?.release()
            val localFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "sajwa_adhan.mp3")
            val uri = if (localFile.exists() && localFile.length() > 0) Uri.fromFile(localFile)
                else intent?.getStringExtra("audio_uri")?.let { Uri.parse(it) }
                ?: Uri.parse("android.resource://$packageName/" + R.raw.adhan)
            player = MediaPlayer().apply {
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
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.sajwa.app.action.STOP_ADHAN"
        const val NOTIFICATION_ID = 4201
    }
}
