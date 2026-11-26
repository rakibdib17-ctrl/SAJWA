package com.sajwa.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.data.DateComponents
import com.batoulapps.adhan.PrayerTimes
import java.util.Calendar

object PrayerAlarmScheduler {
    fun schedule(context: Context, coordinates: Coordinates, parameters: CalculationParameters): Boolean {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) return false
        for (offset in 0..6) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time
            val times = PrayerTimes(coordinates, DateComponents.from(date), parameters)
            val prayers = listOf("Fajr" to times.fajr, "Dhuhr" to times.dhuhr, "Asr" to times.asr, "Maghrib" to times.maghrib, "Isha" to times.isha)
            prayers.forEachIndexed { index, pair ->
                val at = pair.second.time
                if (at > System.currentTimeMillis()) {
                    val requestCode = ((offset + 1) * 10) + index
                    val alarmIntent = Intent(context, PrayerAlarmReceiver::class.java).putExtra("prayer", pair.first)
                    val pending = PendingIntent.getBroadcast(context, requestCode, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            }
        }
        return true
    }
}