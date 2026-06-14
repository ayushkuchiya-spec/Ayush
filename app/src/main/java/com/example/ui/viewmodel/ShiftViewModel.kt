package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.alarm.AudioSoundPlayer
import com.example.data.entity.AlarmSetting
import com.example.data.entity.OverrideDay
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType
import com.example.data.repository.ShiftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ShiftViewModel(
    private val repository: ShiftRepository,
    private val app: Application
) : AndroidViewModel(app) {

    private val TAG = "ShiftViewModel"

    // State Flows from Database
    val allRotas: StateFlow<List<Rota>> = repository.allRotas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRota: StateFlow<Rota?> = repository.activeRota
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allOverrideDays: StateFlow<List<OverrideDay>> = repository.allOverrideDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Shift positions for active Rota
    val activeRotaPositions: StateFlow<List<ShiftPosition>> = activeRota
        .flatMapLatest { rota ->
            if (rota != null) {
                repository.getPositionsForRotaFlow(rota.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Alarm settings for active Rota
    val activeRotaSettings: StateFlow<List<AlarmSetting>> = activeRota
        .flatMapLatest { rota ->
            if (rota != null) {
                repository.getSettingsForRotaFlow(rota.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Upcoming Alarms List
    private val _upcomingAlarms = MutableStateFlow<List<AlarmScheduler.ScheduledAlarmInfo>>(emptyList())
    val upcomingAlarms: StateFlow<List<AlarmScheduler.ScheduledAlarmInfo>> = _upcomingAlarms.asStateFlow()

    // Countdown Text
    private val _countdownText = MutableStateFlow("No active alarms")
    val countdownText: StateFlow<String> = _countdownText.asStateFlow()

    // Sound test state
    private val _isTestingSound = MutableStateFlow(false)
    val isTestingSound: StateFlow<Boolean> = _isTestingSound.asStateFlow()

    init {
        // Initialize active rosters with presets if empty
        viewModelScope.launch {
            repository.allRotas.collect { list ->
                if (list.isEmpty()) {
                    // Populate template rotas
                    repository.createPresetRota(0) // Standard
                    val activeId = repository.createPresetRota(1) // 10-day
                    repository.createPresetRota(2) // Continental 14-day

                    // Select second one as default active
                    repository.setActiveRota(activeId.toInt())
                    refreshAlarms()
                }
            }
        }

        // Periodically refresh the visual countdown ticker and upcoming events lists
        viewModelScope.launch {
            while (true) {
                tickerUpdate()
                delay(1000)
            }
        }

        // Re-calculate alarms whenever active rota, positions, or settings change in database
        viewModelScope.launch {
            combine(activeRota, activeRotaPositions, activeRotaSettings, allOverrideDays) { _, _, _, _ -> }
                .collect {
                    refreshAlarms()
                }
        }
    }

    fun refreshAlarms() {
        viewModelScope.launch {
            AlarmScheduler.scheduleNextAlarm(app)
            val list = AlarmScheduler.getUpcomingAlarms(app, 5)
            _upcomingAlarms.value = list
        }
    }

    private fun tickerUpdate() {
        val nextTime = AlarmScheduler.getNextAlarmScheduledTime(app)
        if (nextTime <= 0) {
            _countdownText.value = "Alarms disabled"
            return
        }

        val now = System.currentTimeMillis()
        val diff = nextTime - now

        if (diff <= 0) {
            _countdownText.value = "Triggering now..."
            refreshAlarms()
        } else {
            val duration = Duration.ofMillis(diff)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            val seconds = duration.getSeconds() % 60

            val countdownStr = buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0 || days > 0) append("${hours}h ")
                append("${minutes}m ${seconds}s")
            }
            _countdownText.value = countdownStr
        }
    }

    // --- Actions ---

    fun setActiveRota(rotaId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setActiveRota(rotaId)
            refreshAlarms()
        }
    }

    fun deleteRota(rota: Rota) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRota(rota)
            // If the active rota got deleted, set the first available as active
            val currentRotas = repository.allRotas.firstOrNull() ?: emptyList()
            val active = repository.getActiveRotaDirect()
            if (active == null && currentRotas.isNotEmpty()) {
                repository.setActiveRota(currentRotas[0].id)
            }
            refreshAlarms()
        }
    }

    fun createCustomRota(name: String, cycleLength: Int, startDate: Long, shifts: List<Pair<ShiftType, Pair<String, String>>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val parentId = repository.insertRota(
                Rota(
                    name = name,
                    cycleLength = cycleLength,
                    startDateMillis = startDate,
                    isActive = false
                )
            ).toInt()

            val positions = shifts.mapIndexed { idx, pair ->
                ShiftPosition(
                    rotaId = parentId,
                    positionIndex = idx,
                    shiftType = pair.first,
                    startTime = pair.second.first,
                    endTime = pair.second.second,
                    label = "${pair.first.displayName.first()}${idx + 1}"
                )
            }
            repository.insertShiftPositions(positions)

            // Setup default alarm configs
            val alarmSettings = listOf(
                AlarmSetting(
                    rotaId = parentId,
                    shiftType = ShiftType.MORNING,
                    isEnabled = true,
                    minutesBefore = 30,
                    sound = "gentle",
                    volume = 80,
                    isVibrationEnabled = true,
                    customMessage = "Time to rise for Morning Shift!"
                ),
                AlarmSetting(
                    rotaId = parentId,
                    shiftType = ShiftType.AFTERNOON,
                    isEnabled = true,
                    minutesBefore = 30,
                    sound = "chime",
                    volume = 75,
                    isVibrationEnabled = true,
                    customMessage = "Shift is starting shortly!"
                ),
                AlarmSetting(
                    rotaId = parentId,
                    shiftType = ShiftType.NIGHT,
                    isEnabled = true,
                    minutesBefore = 45,
                    sound = "loud",
                    volume = 90,
                    isVibrationEnabled = true,
                    customMessage = "Rise and shine for Night Shift!"
                )
            )
            repository.insertAlarmSettings(alarmSettings)

            // Auto-activate the created rota
            repository.setActiveRota(parentId)
            refreshAlarms()
        }
    }

    fun createPresetRota(presetType: Int, customName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val parentId = repository.createPresetRota(presetType, customName)
            repository.setActiveRota(parentId.toInt())
            refreshAlarms()
        }
    }

    fun updateAlarmSetting(setting: AlarmSetting) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAlarmSetting(setting)
            refreshAlarms()
        }
    }

    fun toggleAlarmSetting(setting: AlarmSetting, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAlarmSetting(setting.copy(isEnabled = isEnabled))
            refreshAlarms()
        }
    }

    // Override alarm for specific day
    fun toggleDayAlarmDisabledOverride(date: LocalDate, disable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = date.toString()
            val existing = repository.getOverrideDayDirect(dateStr)
            if (disable) {
                if (existing != null) {
                    repository.addOverrideDay(existing.copy(isAlarmDisabled = true))
                } else {
                    repository.addOverrideDay(OverrideDay(dateString = dateStr, isAlarmDisabled = true))
                }
            } else {
                if (existing != null) {
                    if (existing.customShiftType == null) {
                        repository.removeOverrideDay(dateStr)
                    } else {
                        repository.addOverrideDay(existing.copy(isAlarmDisabled = false))
                    }
                }
            }
            refreshAlarms()
        }
    }

    // Swap shift duty for specific day
    fun overrideDayShiftType(date: LocalDate, shiftType: ShiftType?) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = date.toString()
            val existing = repository.getOverrideDayDirect(dateStr)
            if (shiftType == null) {
                if (existing != null) {
                    if (existing.isAlarmDisabled) {
                        repository.addOverrideDay(existing.copy(customShiftType = null))
                    } else {
                        repository.removeOverrideDay(dateStr)
                    }
                }
            } else {
                if (existing != null) {
                    repository.addOverrideDay(existing.copy(customShiftType = shiftType))
                } else {
                    repository.addOverrideDay(OverrideDay(dateString = dateStr, customShiftType = shiftType))
                }
            }
            refreshAlarms()
        }
    }

    // --- Sound Testing ---

    fun testSound(soundType: String, volume: Int) {
        _isTestingSound.value = true
        AudioSoundPlayer.startSound(app, soundType, volume, true)
    }

    fun stopTestSound() {
        _isTestingSound.value = false
        AudioSoundPlayer.stopSound(app)
    }

    // --- JSON Export / Import ---

    fun exportRotaAsJson(rota: Rota, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val positions = repository.getPositionsForRotaDirect(rota.id)
                val settings = repository.getSettingsForRotaDirect(rota.id)

                val root = JSONObject()
                root.put("version", 1)
                root.put("name", rota.name)
                root.put("cycleLength", rota.cycleLength)
                root.put("startDateMillis", rota.startDateMillis)

                val posArr = JSONArray()
                positions.forEach { pos ->
                    val posObj = JSONObject()
                    posObj.put("index", pos.positionIndex)
                    posObj.put("shiftType", pos.shiftType.name)
                    posObj.put("startTime", pos.startTime)
                    posObj.put("endTime", pos.endTime)
                    posObj.put("label", pos.label)
                    posArr.put(posObj)
                }
                root.put("positions", posArr)

                val setArr = JSONArray()
                settings.forEach { set ->
                    val setObj = JSONObject()
                    setObj.put("shiftType", set.shiftType.name)
                    setObj.put("isEnabled", set.isEnabled)
                    setObj.put("minutesBefore", set.minutesBefore)
                    setObj.put("sound", set.sound)
                    setObj.put("volume", set.volume)
                    setObj.put("isVibrator", set.isVibrationEnabled)
                    setObj.put("message", set.customMessage)
                    setArr.put(setObj)
                }
                root.put("settings", setArr)

                val jsonStr = root.toString(2)
                callback(jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                callback("")
            }
        }
    }

    fun importRotaFromJson(jsonString: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonString)
                val name = root.getString("name")
                val cycleLength = root.getInt("cycleLength")
                val startDateMillis = root.optLong("startDateMillis", System.currentTimeMillis())

                val parentId = repository.insertRota(
                    Rota(
                        name = name,
                        cycleLength = cycleLength,
                        startDateMillis = startDateMillis,
                        isActive = false
                    )
                ).toInt()

                val posArr = root.getJSONArray("positions")
                val positionsList = mutableListOf<ShiftPosition>()
                for (i in 0 until posArr.length()) {
                    val posObj = posArr.getJSONObject(i)
                    positionsList.add(
                        ShiftPosition(
                            rotaId = parentId,
                            positionIndex = posObj.getInt("index"),
                            shiftType = ShiftType.valueOf(posObj.getString("shiftType")),
                            startTime = posObj.getString("startTime"),
                            endTime = posObj.getString("endTime"),
                            label = posObj.optString("label", "")
                        )
                    )
                }
                repository.insertShiftPositions(positionsList)

                val setArr = root.getJSONArray("settings")
                val settingsList = mutableListOf<AlarmSetting>()
                for (i in 0 until setArr.length()) {
                    val setObj = setArr.getJSONObject(i)
                    settingsList.add(
                        AlarmSetting(
                            rotaId = parentId,
                            shiftType = ShiftType.valueOf(setObj.getString("shiftType")),
                            isEnabled = setObj.getBoolean("isEnabled"),
                            minutesBefore = setObj.getInt("minutesBefore"),
                            sound = setObj.getString("sound"),
                            volume = setObj.getInt("volume"),
                            isVibrationEnabled = setObj.optBoolean("isVibrator", true),
                            customMessage = setObj.getString("message")
                        )
                    )
                }
                repository.insertAlarmSettings(settingsList)

                // Select imported rota as active
                repository.setActiveRota(parentId)
                refreshAlarms()
                callback(true)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                callback(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cease any active synthesized test sounds
        AudioSoundPlayer.stopSound(app)
    }
}

class ShiftViewModelFactory(
    private val repository: ShiftRepository,
    private val app: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShiftViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShiftViewModel(repository, app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
