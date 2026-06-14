package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.entity.AlarmSetting
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val PREFS_NAME = "ShiftAlarmPrefs"
    private const val KEY_NEXT_ALARM_TIME = "next_alarm_time"
    private const val KEY_NEXT_ALARM_MESSAGE = "next_alarm_message"
    private const val KEY_NEXT_ALARM_TYPE = "next_alarm_type"

    data class ScheduledAlarmInfo(
        val epochMillis: Long,
        val message: String,
        val shiftType: String,
        val sound: String,
        val volume: Int,
        val isVibrate: Boolean
    )

    fun getShiftPositionForDate(date: LocalDate, rota: Rota, positions: List<ShiftPosition>): ShiftPosition? {
        if (positions.isEmpty()) return null
        val startLocalDate = Instant.ofEpochMilli(rota.startDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val daysBetween = ChronoUnit.DAYS.between(startLocalDate, date)
        val cycleLen = rota.cycleLength
        if (cycleLen <= 0) return null

        var offset = (daysBetween % cycleLen).toInt()
        if (offset < 0) {
            offset += cycleLen
        }
        return positions.firstOrNull { it.positionIndex == offset }
    }

    /**
     * Finds the upcoming list of alarms (e.g. next 5) based on current active rota & settings
     */
    suspend fun getUpcomingAlarms(context: Context, count: Int = 5): List<ScheduledAlarmInfo> {
        val db = AppDatabase.getDatabase(context)
        val rotaDao = db.rotaDao()

        val activeRota = rotaDao.getActiveRota() ?: return emptyList()
        val positions = rotaDao.getShiftPositionsDirect(activeRota.id)
        val settings = rotaDao.getAlarmSettingsDirect(activeRota.id)

        if (positions.isEmpty() || settings.isEmpty()) return emptyList()

        val upcomingList = mutableListOf<ScheduledAlarmInfo>()
        var checkDate = LocalDate.now()
        val nowMillis = System.currentTimeMillis()

        // Check for next 60 days
        for (i in 0..60) {
            val dateStr = checkDate.toString()
            val overrideDay = rotaDao.getOverrideDay(dateStr)

            // If alarm is explicitly disabled for today/this override day, skip
            if (overrideDay?.isAlarmDisabled == true) {
                checkDate = checkDate.plusDays(1)
                continue
            }

            // Determine shift type for this checkDate
            val shiftType = if (overrideDay?.customShiftType != null) {
                overrideDay.customShiftType
            } else {
                getShiftPositionForDate(checkDate, activeRota, positions)?.shiftType
            }

            if (shiftType != null && shiftType != ShiftType.OFF) {
                val alarmSetting = settings.firstOrNull { it.shiftType == shiftType }
                if (alarmSetting != null && alarmSetting.isEnabled) {
                    // Start time
                    val matchingPosition = positions.firstOrNull { it.shiftType == shiftType }
                    val startTimeStr = matchingPosition?.startTime ?: when(shiftType) {
                        ShiftType.MORNING -> "06:00"
                        ShiftType.AFTERNOON -> "14:00"
                        ShiftType.NIGHT -> "22:00"
                        else -> "00:00"
                    }

                    try {
                        val timeParts = startTimeStr.split(":")
                        if (timeParts.size == 2) {
                            val hour = timeParts[0].toInt()
                            val minute = timeParts[1].toInt()
                            val shiftTime = LocalDateTime.of(checkDate, LocalTime.of(hour, minute))
                            val alarmTime = shiftTime.minusMinutes(alarmSetting.minutesBefore.toLong())

                            val alarmEpoch = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                            if (alarmEpoch > nowMillis) {
                                upcomingList.add(
                                    ScheduledAlarmInfo(
                                        epochMillis = alarmEpoch,
                                        message = alarmSetting.customMessage.ifEmpty { "Time to rise for work!" },
                                        shiftType = shiftType.displayName,
                                        sound = alarmSetting.sound,
                                        volume = alarmSetting.volume,
                                        isVibrate = alarmSetting.isVibrationEnabled
                                    )
                                )
                                if (upcomingList.size >= count) break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing time", e)
                    }
                }
            }
            checkDate = checkDate.plusDays(1)
        }

        return upcomingList.sortedBy { it.epochMillis }
    }

    /**
     * Re-calculate and schedule the exact Alarm clock trigger with the OS
     */
    suspend fun scheduleNextAlarm(context: Context) {
        val appContext = context.applicationContext ?: context
        val upcoming = getUpcomingAlarms(appContext, 1)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, AlarmReceiver::class.java)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(appContext, 1001, intent, flag)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (upcoming.isNotEmpty()) {
            val nextAlarm = upcoming[0]
            Log.d(TAG, "Scheduling next alarm at: ${Instant.ofEpochMilli(nextAlarm.epochMillis)}")

            // Record to SharedPreferences so the views have easy reading access
            prefs.edit()
                .putLong(KEY_NEXT_ALARM_TIME, nextAlarm.epochMillis)
                .putString(KEY_NEXT_ALARM_MESSAGE, nextAlarm.message)
                .putString(KEY_NEXT_ALARM_TYPE, nextAlarm.shiftType)
                .apply()

            // Schedule with system AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm.epochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm.epochMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarm.epochMillis,
                    pendingIntent
                )
            }
        } else {
            Log.d(TAG, "No upcoming alarms found, canceling schedule.")
            alarmManager.cancel(pendingIntent)
            prefs.edit()
                .putLong(KEY_NEXT_ALARM_TIME, 0L)
                .putString(KEY_NEXT_ALARM_MESSAGE, "")
                .putString(KEY_NEXT_ALARM_TYPE, "")
                .apply()
        }
    }

    fun getNextAlarmScheduledTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_NEXT_ALARM_TIME, 0L)
    }

    fun getNextAlarmScheduledType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NEXT_ALARM_TYPE, "") ?: ""
    }
}
