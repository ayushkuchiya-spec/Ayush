package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_settings")
data class AlarmSetting(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rotaId: Int,
    val shiftType: ShiftType,
    val isEnabled: Boolean,
    val minutesBefore: Int, // e.g., 30 means 30 mins before shift starts
    val sound: String, // e.g., "beep", "chime", "gentle", "loud"
    val volume: Int, // 0 to 100
    val isVibrationEnabled: Boolean,
    val customMessage: String
)
