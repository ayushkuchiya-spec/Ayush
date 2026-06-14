package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "override_days")
data class OverrideDay(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String, // Format: "YYYY-MM-DD"
    val isAlarmDisabled: Boolean = false,
    val customShiftType: ShiftType? = null // if they swap a shift for that day
)
