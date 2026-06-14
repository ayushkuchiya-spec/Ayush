package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext ?: context
            Log.d("BootReceiver", "Device rebooted, re-scheduling active shift alarms.")
            CoroutineScope(Dispatchers.IO).launch {
                AlarmScheduler.scheduleNextAlarm(appContext)
            }
        }
    }
}
