package com.sajwa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.stopService(Intent(context, AdhanPlaybackService::class.java))
    }
}
