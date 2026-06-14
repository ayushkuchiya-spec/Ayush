package com.example.data.repository

import com.example.data.database.RotaDao
import com.example.data.entity.AlarmSetting
import com.example.data.entity.OverrideDay
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType
import kotlinx.coroutines.flow.Flow

class ShiftRepository(private val rotaDao: RotaDao) {

    val allRotas: Flow<List<Rota>> = rotaDao.getAllRotas()
    val activeRota: Flow<Rota?> = rotaDao.getActiveRotaFlow()
    val allOverrideDays: Flow<List<OverrideDay>> = rotaDao.getAllOverrideDays()

    suspend fun getActiveRotaDirect(): Rota? = rotaDao.getActiveRota()

    suspend fun insertRota(rota: Rota): Long = rotaDao.insertRota(rota)

    suspend fun updateRota(rota: Rota) = rotaDao.updateRota(rota)

    suspend fun deleteRota(rota: Rota) {
        rotaDao.deleteShiftPositionsForRota(rota.id)
        rotaDao.deleteRota(rota)
    }

    suspend fun setActiveRota(rotaId: Int) = rotaDao.setActiveRota(rotaId)

    suspend fun insertShiftPositions(positions: List<ShiftPosition>) {
        rotaDao.insertShiftPositions(positions)
    }

    suspend fun getPositionsForRotaDirect(rotaId: Int): List<ShiftPosition> {
        return rotaDao.getShiftPositionsDirect(rotaId)
    }

    fun getPositionsForRotaFlow(rotaId: Int): Flow<List<ShiftPosition>> {
        return rotaDao.getShiftPositionsForRota(rotaId)
    }

    suspend fun getSettingsForRotaDirect(rotaId: Int): List<AlarmSetting> {
        return rotaDao.getAlarmSettingsDirect(rotaId)
    }

    fun getSettingsForRotaFlow(rotaId: Int): Flow<List<AlarmSetting>> {
        return rotaDao.getAlarmSettingsForRota(rotaId)
    }

    suspend fun insertAlarmSettings(settings: List<AlarmSetting>) {
        rotaDao.insertAlarmSettings(settings)
    }

    suspend fun updateAlarmSetting(setting: AlarmSetting) {
        rotaDao.updateAlarmSetting(setting)
    }

    suspend fun addOverrideDay(overrideDay: OverrideDay) {
        rotaDao.insertOverrideDay(overrideDay)
    }

    suspend fun removeOverrideDay(dateString: String) {
        rotaDao.deleteOverrideDay(dateString)
    }

    suspend fun getOverrideDayDirect(dateString: String): OverrideDay? {
        return rotaDao.getOverrideDay(dateString)
    }

    // --- Helper to create and insert Preset Rotas ---
    suspend fun createPresetRota(presetIndex: Int, customName: String? = null): Long {
        val today = System.currentTimeMillis()
        val (name, length, list) = when (presetIndex) {
            0 -> Triple(
                customName ?: "Standard 7-Day Cycle",
                7,
                listOf(
                    Pair(ShiftType.MORNING, "06:00" to "14:00"),
                    Pair(ShiftType.MORNING, "06:00" to "14:00"),
                    Pair(ShiftType.AFTERNOON, "14:00" to "22:00"),
                    Pair(ShiftType.AFTERNOON, "14:00" to "22:00"),
                    Pair(ShiftType.NIGHT, "22:00" to "06:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00")
                )
            )
            1 -> Triple(
                customName ?: "10-Day Rapid Rotation",
                10,
                listOf(
                    Pair(ShiftType.MORNING, "06:00" to "14:00"),
                    Pair(ShiftType.MORNING, "06:00" to "14:00"),
                    Pair(ShiftType.AFTERNOON, "14:00" to "22:00"),
                    Pair(ShiftType.AFTERNOON, "14:00" to "22:00"),
                    Pair(ShiftType.NIGHT, "22:00" to "06:00"),
                    Pair(ShiftType.NIGHT, "22:00" to "06:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00")
                )
            )
            2 -> Triple(
                customName ?: "Continental 14-Day (2-2-3)",
                14,
                listOf(
                    Pair(ShiftType.MORNING, "06:00" to "18:00"),
                    Pair(ShiftType.MORNING, "06:00" to "18:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.NIGHT, "18:00" to "06:00"),
                    Pair(ShiftType.NIGHT, "18:00" to "06:00"),
                    Pair(ShiftType.NIGHT, "18:00" to "06:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.MORNING, "06:00" to "18:00"),
                    Pair(ShiftType.MORNING, "06:00" to "18:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00")
                )
            )
            else -> Triple(
                customName ?: "Standard 5:2 Weekly Pattern",
                7,
                listOf(
                    Pair(ShiftType.MORNING, "08:00" to "16:00"),
                    Pair(ShiftType.MORNING, "08:00" to "16:00"),
                    Pair(ShiftType.MORNING, "08:00" to "16:00"),
                    Pair(ShiftType.MORNING, "08:00" to "16:00"),
                    Pair(ShiftType.MORNING, "08:00" to "16:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00"),
                    Pair(ShiftType.OFF, "00:00" to "00:00")
                )
            )
        }

        // Insert Rota, default inactive (will trigger action manually or make first one active)
        val parentId = insertRota(Rota(name = name, cycleLength = length, startDateMillis = today, isActive = false)).toInt()

        // Insert positions
        val positions = list.mapIndexed { idx, pair ->
            ShiftPosition(
                rotaId = parentId,
                positionIndex = idx,
                shiftType = pair.first,
                startTime = pair.second.first,
                endTime = pair.second.second,
                label = "${pair.first.displayName.first()}${idx + 1}"
            )
        }
        insertShiftPositions(positions)

        // Insert default Alarm Settings
        val alarmSettings = listOf(
            AlarmSetting(
                rotaId = parentId,
                shiftType = ShiftType.MORNING,
                isEnabled = true,
                minutesBefore = 30,
                sound = "gentle",
                volume = 80,
                isVibrationEnabled = true,
                customMessage = "Time to wake up for Morning Shift!"
            ),
            AlarmSetting(
                rotaId = parentId,
                shiftType = ShiftType.AFTERNOON,
                isEnabled = true,
                minutesBefore = 45,
                sound = "chime",
                volume = 75,
                isVibrationEnabled = true,
                customMessage = "Afternoon shift is starting soon!"
            ),
            AlarmSetting(
                rotaId = parentId,
                shiftType = ShiftType.NIGHT,
                isEnabled = true,
                minutesBefore = 60,
                sound = "loud",
                volume = 90,
                isVibrationEnabled = true,
                customMessage = "Wake up for your Night Shift!"
            )
        )
        insertAlarmSettings(alarmSettings)

        return parentId.toLong()
    }
}
