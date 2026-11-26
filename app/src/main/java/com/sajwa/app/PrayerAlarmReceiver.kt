package com.sajwa.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer") ?: "Prayer"
        val audioIntent = Intent(context, AdhanPlaybackService::class.java).putExtra("prayer", prayer)
        try { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(audioIntent) else context.startService(audioIntent) } catch (_: Exception) { }
        PrayerAlarmScheduler.rescheduleFromSavedLocation(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sajwa_prayer_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Prayer time alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "SAJWA prayer time reminders"
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            manager.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$prayer time")
            .setContentText("It is time for $prayer prayer.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        manager.notify(prayer.hashCode(), notification)
    }
}