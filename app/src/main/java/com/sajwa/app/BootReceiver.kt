package com.sajwa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                PrayerAlarmScheduler.rescheduleFromSavedLocation(context)
            }
        }
    }
}
