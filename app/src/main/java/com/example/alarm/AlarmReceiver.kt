package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext ?: context
        val action = intent.action
        Log.d(TAG, "AlarmReceiver received action: $action")

        if (action == ACTION_DISMISS) {
            // Dismiss active alarm sound
            AudioSoundPlayer.stopSound(appContext)
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Alarm dismissed by user action.")
            return
        }

        // Default: Trigger alarm
        triggerAlarmNotification(appContext)

        // Automatically schedule the subsequent alarm in background state
        CoroutineScope(Dispatchers.IO).launch {
            AlarmScheduler.scheduleNextAlarm(appContext)
        }
    }

    private fun triggerAlarmNotification(context: Context) {
        val appContext = context.applicationContext ?: context
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(appContext, nm)

        // Fetch alarm configuration details cached by scheduler
        val message = AlarmScheduler.getNextAlarmScheduledType(appContext) + " Shift Alarm"
        val pref = appContext.getSharedPreferences("ShiftAlarmPrefs", Context.MODE_PRIVATE)
        val customMsg = pref.getString("next_alarm_message", "Wake up, shift is starting!") ?: "Wake up!"

        // Intent to open Main app when clicking notification body
        val appIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val appPendingIntent = PendingIntent.getActivity(
            appContext,
            2001,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Intent to trigger immediate dismiss
        val dismissIntent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            appContext,
            2002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Build elegant Material alert notification card
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(message)
            .setContentText(customMsg)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(appPendingIntent, true)
            .setContentIntent(appPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss Alarm",
                dismissPendingIntent
            )

        // Sound config cached
        val prefs = appContext.getSharedPreferences("ShiftAlarmPrefs", Context.MODE_PRIVATE)
        val soundStr = prefs.getString("next_alarm_sound", "beep") ?: "beep"
        val volume = prefs.getInt("next_alarm_volume", 80)
        val isVibrate = prefs.getBoolean("next_alarm_vibrate", true)

        // Trigger synth alarm sound and vibrator loop
        AudioSoundPlayer.startSound(appContext, soundStr, volume, isVibrate)

        // Publish alert
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shift Duty Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers precise wake up warnings for roster duty events"
                enableLights(true)
                enableVibration(false) // Handle manually via custom AudioSoundPlayer patterns
                setSound(null, null)   // Avoid double ringtone collisions
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val CHANNEL_ID = "shift_duty_alarm_channel_id"
        private const val NOTIFICATION_ID = 8808

        const val ACTION_DISMISS = "com.example.alarm.ACTION_DISMISS_ALARM"
    }
}
