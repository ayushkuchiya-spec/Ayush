package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShiftViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotaSetupScreen(
    viewModel: ShiftViewModel,
    modifier: Modifier = Modifier
) {
    val rotas by viewModel.allRotas.collectAsStateWithLifecycle()
    val activeRota by viewModel.activeRota.collectAsStateWithLifecycle()

    var showWizard by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shift Rotas",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!showWizard) {
                    Button(
                        onClick = { showWizard = true },
                        modifier = Modifier.testTag("add_custom_rota_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Icon")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Rota")
                    }
                }
            }
        }

        if (showWizard) {
            item {
                CreateRotaWizardCard(
                    onDismiss = { showWizard = false },
                    onSave = { name, len, start, shifts ->
                        viewModel.createCustomRota(name, len, start, shifts)
                        showWizard = false
                    }
                )
            }
        }

        item {
            Text(
                text = "Preset Templates",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChoiceChip(label = "Standard 7-Day", onClick = { viewModel.createPresetRota(0) })
                PresetChoiceChip(label = "10-Day Rapid Duty", onClick = { viewModel.createPresetRota(1) })
                PresetChoiceChip(label = "Continental (14d)", onClick = { viewModel.createPresetRota(2) })
                PresetChoiceChip(label = "Office 5:2 Week", onClick = { viewModel.createPresetRota(3) })
            }
        }

        item {
            Text(
                text = "All Rotations",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (rotas.isEmpty()) {
            item {
                Text(
                    text = "No saved layouts. Use templates above or click New Rota.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(rotas) { rota ->
                val isActive = activeRota?.id == rota.id
                RotaCard(
                    rota = rota,
                    isActive = isActive,
                    onSelect = { viewModel.setActiveRota(rota.id) },
                    onDelete = { viewModel.deleteRota(rota) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RotaCard(
    rota: Rota,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val date = Instant.ofEpochMilli(rota.startDateMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val formattedDate = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rota_item_card_${rota.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rota.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${rota.cycleLength}-Day Rotation cycle",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Reference anchor: $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive) {
                    IconButton(onClick = onSelect) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Activate",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun PresetChoiceChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateRotaWizardCard(
    onDismiss: () -> Unit,
    onSave: (String, Int, Long, List<Pair<ShiftType, Pair<String, String>>>) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var cycleLength by remember { mutableStateOf(7) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    var shiftsState = remember(cycleLength) {
        mutableStateListOf<Pair<ShiftType, Pair<String, String>>>().apply {
            for (i in 0 until cycleLength) {
                // Default pattern: mornings, then afternoons, then night, then offs
                val defaultShift = when {
                    i % 4 == 0 -> ShiftType.MORNING
                    i % 4 == 1 -> ShiftType.AFTERNOON
                    i % 4 == 2 -> ShiftType.NIGHT
                    else -> ShiftType.OFF
                }

                val defaultTimes = when (defaultShift) {
                    ShiftType.MORNING -> "06:00" to "14:00"
                    ShiftType.AFTERNOON -> "14:00" to "22:00"
                    ShiftType.NIGHT -> "22:00" to "06:00"
                    ShiftType.OFF -> "00:00" to "00:00"
                }

                add(Pair(defaultShift, defaultTimes))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("create_rota_wizard"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "New Custom Rota",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rota Theme Name") },
                placeholder = { Text("e.g. My Steel Mill Schedule") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rota_name_input")
            )

            // Length adjustment slider
            Column {
                Text(
                    text = "Rotation Cycle Length: $cycleLength days",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = cycleLength.toFloat(),
                    onValueChange = { cycleLength = it.toInt() },
                    valueRange = 3f..24f,
                    steps = 20,
                    modifier = Modifier.testTag("cycle_length_slider")
                )
            }

            // Anchor Date Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Roster Starts On:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                selectedDate = LocalDate.of(year, month + 1, day)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Dates", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Start Date", fontSize = 12.sp)
                }
            }

            HorizontalDivider()

            Text(
                text = "Configure Day-by-Day Shifts",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )

            // Day configuration items
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0 until cycleLength) {
                    val currentShift = shiftsState.getOrNull(i) ?: continue

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Day ${i + 1}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(48.dp)
                            )

                            // Quick choice chips
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (currentShift.first) {
                                            ShiftType.MORNING -> ShiftMorningContainer
                                            ShiftType.AFTERNOON -> ShiftAfternoonContainer
                                            ShiftType.NIGHT -> ShiftNightContainer
                                            ShiftType.OFF -> ShiftOffContainer
                                        }
                                    )
                                    .clickable {
                                        // Cycle Duty categories
                                        val next = when (currentShift.first) {
                                            ShiftType.MORNING -> ShiftType.AFTERNOON
                                            ShiftType.AFTERNOON -> ShiftType.NIGHT
                                            ShiftType.NIGHT -> ShiftType.OFF
                                            ShiftType.OFF -> ShiftType.MORNING
                                        }
                                        val nextTimes = when (next) {
                                            ShiftType.MORNING -> "06:00" to "14:00"
                                            ShiftType.AFTERNOON -> "14:00" to "22:00"
                                            ShiftType.NIGHT -> "22:00" to "06:00"
                                            ShiftType.OFF -> "00:00" to "00:00"
                                        }
                                        shiftsState[i] = Pair(next, nextTimes)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = currentShift.first.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (currentShift.first) {
                                        ShiftType.MORNING -> ShiftMorning
                                        ShiftType.AFTERNOON -> ShiftAfternoon
                                        ShiftType.NIGHT -> ShiftNight
                                        ShiftType.OFF -> ShiftOff
                                    }
                                )
                            }

                            // Time buttons if not Off
                            if (currentShift.first != ShiftType.OFF) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimePickerButton(currentShift.second.first) { updatedHourMin ->
                                        shiftsState[i] = Pair(currentShift.first, updatedHourMin to currentShift.second.second)
                                    }
                                    Text("-", fontSize = 11.sp)
                                    TimePickerButton(currentShift.second.second) { updatedHourMin ->
                                        shiftsState[i] = Pair(currentShift.first, currentShift.second.first to updatedHourMin)
                                    }
                                }
                            } else {
                                Text("Off (No Alarm)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val secureName = name.ifEmpty { "Rota Cycle Length $cycleLength" }
                        val selectedEpoch = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onSave(secureName, cycleLength, selectedEpoch, shiftsState.toList())
                    },
                    enabled = true,
                    modifier = Modifier.testTag("save_custom_rota_btn")
                ) {
                    Text("Create Rota")
                }
            }
        }
    }
}

@Composable
fun TimePickerButton(
    timeStr: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val parts = timeStr.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .clickable {
                TimePickerDialog(
                    context,
                    { _, h, m ->
                        val formatted = String.format("%02d:%02d", h, m)
                        onTimeSelected(formatted)
                    },
                    hour,
                    minute,
                    true
                ).show()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(timeStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
)
