package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.AlarmSetting
import com.example.data.entity.ShiftType
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShiftViewModel

@Composable
fun AlarmsConfigurationScreen(
    viewModel: ShiftViewModel,
    modifier: Modifier = Modifier
) {
    val activeRota by viewModel.activeRota.collectAsStateWithLifecycle()
    val settings by viewModel.activeRotaSettings.collectAsStateWithLifecycle()
    val isTestingSound by viewModel.isTestingSound.collectAsStateWithLifecycle()

    var testingSoundType by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Alarms Configuration",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure trigger offsets, sounds, haptic vibration, and messages per shift category.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (activeRota == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Please activate a Rota schedule inside [Rota Setup] to configure shift alarms.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(settings) { setting ->
                AlarmSettingCollapseCard(
                    setting = setting,
                    isTestingAnySound = isTestingSound,
                    isTestingThisSound = isTestingSound && testingSoundType == setting.shiftType.name,
                    onUpdate = { updatedSetting ->
                        viewModel.updateAlarmSetting(updatedSetting)
                    },
                    onTestSound = { sound, vol ->
                        testingSoundType = setting.shiftType.name
                        viewModel.testSound(sound, vol)
                    },
                    onStopTest = {
                        viewModel.stopTestSound()
                        testingSoundType = ""
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AlarmSettingCollapseCard(
    setting: AlarmSetting,
    isTestingAnySound: Boolean,
    isTestingThisSound: Boolean,
    onUpdate: (AlarmSetting) -> Unit,
    onTestSound: (String, Int) -> Unit,
    onStopTest: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val shiftColor = when (setting.shiftType) {
        ShiftType.MORNING -> ShiftMorning
        ShiftType.AFTERNOON -> ShiftAfternoon
        ShiftType.NIGHT -> ShiftNight
        ShiftType.OFF -> ShiftOff
    }

    val shiftContainer = when (setting.shiftType) {
        ShiftType.MORNING -> ShiftMorningContainer
        ShiftType.AFTERNOON -> ShiftAfternoonContainer
        ShiftType.NIGHT -> ShiftNightContainer
        ShiftType.OFF -> ShiftOffContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_config_${setting.shiftType.name}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surface
            else shiftContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(shiftColor)
                    )

                    Column {
                        Text(
                            text = setting.shiftType.displayName + " Shift",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (setting.isEnabled) "Alarm rings ${setting.minutesBefore}m before shift start" else "Alarm is disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = setting.isEnabled,
                        onCheckedChange = { onUpdate(setting.copy(isEnabled = it)) },
                        modifier = Modifier.testTag("alarm_toggle_${setting.shiftType.name}")
                    )

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand controls",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded settings content
            if (expanded) {
                HorizontalDivider()

                // Slider for minutes before
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ring Alarm Before Shift:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${setting.minutesBefore} minutes",
                            fontWeight = FontWeight.Bold,
                            color = shiftColor
                        )
                    }
                    Slider(
                        value = setting.minutesBefore.toFloat(),
                        onValueChange = { onUpdate(setting.copy(minutesBefore = it.toInt())) },
                        valueRange = 15f..120f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = shiftColor,
                            activeTrackColor = shiftColor
                        )
                    )
                }

                // Volume slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Alarm Volume:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${setting.volume}%",
                            fontWeight = FontWeight.Bold,
                            color = shiftColor
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Volume icon", tint = shiftColor)
                        Slider(
                            value = setting.volume.toFloat(),
                            onValueChange = { onUpdate(setting.copy(volume = it.toInt())) },
                            valueRange = 10f..100f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = shiftColor,
                                activeTrackColor = shiftColor
                            )
                        )
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Volume icon", tint = shiftColor)
                    }
                }

                // Sound choices
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Alarm Tone Alert:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("beep", "chime", "gentle", "loud").forEach { sound ->
                            val isSel = setting.sound == sound
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSel) shiftColor else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
                                    .clickable { onUpdate(setting.copy(sound = sound)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sound.replaceFirstChar { it.titlecase() },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Haptic Vibration",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Switch(
                        checked = setting.isVibrationEnabled,
                        onCheckedChange = { onUpdate(setting.copy(isVibrationEnabled = it)) }
                    )
                }

                // Custom wake up message input
                OutlinedTextField(
                    value = setting.customMessage,
                    onValueChange = { onUpdate(setting.copy(customMessage = it)) },
                    label = { Text("Alarm Morning Message") },
                    placeholder = { Text("Rise and shine, steel workers!") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Test sound button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isTestingThisSound) {
                        Button(
                            onClick = onStopTest,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Stop sound")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Test")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isTestingAnySound) {
                                    onStopTest()
                                }
                                onTestSound(setting.sound, setting.volume)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = shiftColor)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Test sound")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Sound")
                        }
                    }
                }
            }
        }
    }
}
