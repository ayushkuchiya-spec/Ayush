package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rotas")
data class Rota(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cycleLength: Int,
    val startDateMillis: Long,
    val isActive: Boolean = false
)
