package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_positions")
data class ShiftPosition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rotaId: Int,
    val positionIndex: Int, // 0 to cycleLength-1
    val shiftType: ShiftType,
    val startTime: String, // e.g. "06:00"
    val endTime: String, // e.g. "14:00"
    val label: String = ""
)
