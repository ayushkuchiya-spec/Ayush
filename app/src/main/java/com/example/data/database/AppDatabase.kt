package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.entity.AlarmSetting
import com.example.data.entity.OverrideDay
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType

class Converters {
    @TypeConverter
    fun fromShiftType(value: ShiftType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toShiftType(value: String?): ShiftType? {
        return value?.let {
            try {
                ShiftType.valueOf(it)
            } catch (e: Exception) {
                ShiftType.OFF
            }
        }
    }
}

@Database(
    entities = [Rota::class, ShiftPosition::class, AlarmSetting::class, OverrideDay::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rotaDao(): RotaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shift_alarm_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
