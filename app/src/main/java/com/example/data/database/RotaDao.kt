package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.entity.AlarmSetting
import com.example.data.entity.OverrideDay
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import kotlinx.coroutines.flow.Flow

@Dao
interface RotaDao {

    // --- Rotas ---
    @Query("SELECT * FROM rotas ORDER BY id DESC")
    fun getAllRotas(): Flow<List<Rota>>

    @Query("SELECT * FROM rotas WHERE isActive = 1 LIMIT 1")
    fun getActiveRotaFlow(): Flow<Rota?>

    @Query("SELECT * FROM rotas WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRota(): Rota?

    @Query("SELECT * FROM rotas WHERE id = :id LIMIT 1")
    suspend fun getRotaById(id: Int): Rota?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRota(rota: Rota): Long

    @Update
    suspend fun updateRota(rota: Rota)

    @Delete
    suspend fun deleteRota(rota: Rota)

    @Query("UPDATE rotas SET isActive = 0")
    suspend fun clearAllActive()

    @Transaction
    suspend fun setActiveRota(rotaId: Int) {
        clearAllActive()
        val rota = getRotaById(rotaId)
        if (rota != null) {
            updateRota(rota.copy(isActive = true))
        }
    }

    // --- Shift Positions ---
    @Query("SELECT * FROM shift_positions WHERE rotaId = :rotaId ORDER BY positionIndex ASC")
    fun getShiftPositionsForRota(rotaId: Int): Flow<List<ShiftPosition>>

    @Query("SELECT * FROM shift_positions WHERE rotaId = :rotaId ORDER BY positionIndex ASC")
    suspend fun getShiftPositionsDirect(rotaId: Int): List<ShiftPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShiftPositions(positions: List<ShiftPosition>)

    @Query("DELETE FROM shift_positions WHERE rotaId = :rotaId")
    suspend fun deleteShiftPositionsForRota(rotaId: Int)

    // --- Alarm Settings ---
    @Query("SELECT * FROM alarm_settings WHERE rotaId = :rotaId")
    fun getAlarmSettingsForRota(rotaId: Int): Flow<List<AlarmSetting>>

    @Query("SELECT * FROM alarm_settings WHERE rotaId = :rotaId")
    suspend fun getAlarmSettingsDirect(rotaId: Int): List<AlarmSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmSettings(settings: List<AlarmSetting>)

    @Update
    suspend fun updateAlarmSetting(setting: AlarmSetting)

    // --- Override Days ---
    @Query("SELECT * FROM override_days")
    fun getAllOverrideDays(): Flow<List<OverrideDay>>

    @Query("SELECT * FROM override_days WHERE dateString = :dateString LIMIT 1")
    suspend fun getOverrideDay(dateString: String): OverrideDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrideDay(overrideDay: OverrideDay)

    @Query("DELETE FROM override_days WHERE dateString = :dateString")
    suspend fun deleteOverrideDay(dateString: String)
}
