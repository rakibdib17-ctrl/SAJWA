package com.sajwa.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.data.DateComponents
import com.batoulapps.adhan.PrayerTimes
import java.util.Calendar

object PrayerAlarmScheduler {
    fun schedule(context: Context, coordinates: Coordinates, parameters: CalculationParameters, country: String = "US"): Boolean {
        val prefs = context.getSharedPreferences("sajwa_prayer", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("country", country)
            .putString("madhab", parameters.madhab.name)
            .putString("latitude", coordinates.latitude.toString())
            .putString("longitude", coordinates.longitude.toString())
            .apply()

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

    fun rescheduleFromSavedLocation(context: Context): Boolean {
        val prefs = context.getSharedPreferences("sajwa_prayer", Context.MODE_PRIVATE)
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return false
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return false
        val country = prefs.getString("country", "US") ?: "US"
        val method = when (country.uppercase()) {
            "AE" -> CalculationMethod.DUBAI
            "BD", "PK", "IN", "AF" -> CalculationMethod.KARACHI
            "SA" -> CalculationMethod.UMM_AL_QURA
            "SG", "MY", "ID" -> CalculationMethod.SINGAPORE
            "EG" -> CalculationMethod.EGYPTIAN
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
        val params = method.getParameters()
        params.madhab = if (prefs.getString("madhab", "") == Madhab.HANAFI.name) Madhab.HANAFI else Madhab.SHAFI
        return schedule(context, Coordinates(lat, lon), params, country)
    }
}