package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.AlarmScheduler
import com.example.data.entity.OverrideDay
import com.example.data.entity.Rota
import com.example.data.entity.ShiftPosition
import com.example.data.entity.ShiftType
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShiftViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(
    viewModel: ShiftViewModel,
    modifier: Modifier = Modifier
) {
    val activeRota by viewModel.activeRota.collectAsStateWithLifecycle()
    val positions by viewModel.activeRotaPositions.collectAsStateWithLifecycle()
    val overrides by viewModel.allOverrideDays.collectAsStateWithLifecycle()
    val upcomingAlarms by viewModel.upcomingAlarms.collectAsStateWithLifecycle()
    val countdownText by viewModel.countdownText.collectAsStateWithLifecycle()

    var selectedDateForEdit by remember { mutableStateOf<LocalDate?>(null) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshAlarms()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Status Section ---
        item {
            HeaderStatusCard(
                activeRota = activeRota,
                positions = positions,
                overrides = overrides,
                countdownText = countdownText,
                upcomingAlarms = upcomingAlarms,
                onModifyTodayClick = { selectedDateForEdit = LocalDate.now() }
            )
        }

        // --- Core 30-Day Grid Calendar ---
        item {
            Text(
                text = "Rota Calendar (Next 30 Days)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (activeRota == null) {
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
                            text = "Please create or activate a Rota in [Rota Setup] to view your calendar shifts.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                CalendarGridSection(
                    activeRota = activeRota!!,
                    positions = positions,
                    overrides = overrides,
                    onDateClick = { selectedDateForEdit = it }
                )
            }
        }

        // --- Upcoming Alarm list item headers ---
        item {
            Text(
                text = "Upcoming Alarms",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (upcomingAlarms.isEmpty() || activeRota == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "No alarms configured for this active rota.",
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(upcomingAlarms) { alarm ->
                UpcomingAlarmRow(alarm = alarm)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal view to edit particular days
    selectedDateForEdit?.let { date ->
        DayModifierDialog(
            date = date,
            activeRota = activeRota,
            positions = positions,
            overrides = overrides,
            onDismiss = { selectedDateForEdit = null },
            onSave = { overrideDay, isCleared ->
                if (isCleared) {
                    viewModel.toggleDayAlarmDisabledOverride(date, false)
                    viewModel.overrideDayShiftType(date, null)
                } else {
                    viewModel.toggleDayAlarmDisabledOverride(date, overrideDay.isAlarmDisabled)
                    viewModel.overrideDayShiftType(date, overrideDay.customShiftType)
                }
                selectedDateForEdit = null
            }
        )
    }
}

@Composable
fun HeaderStatusCard(
    activeRota: Rota?,
    positions: List<ShiftPosition>,
    overrides: List<OverrideDay>,
    countdownText: String,
    upcomingAlarms: List<AlarmScheduler.ScheduledAlarmInfo>,
    onModifyTodayClick: () -> Unit
) {
    val today = LocalDate.now()
    val todayOverrides = overrides.firstOrNull { it.dateString == today.toString() }

    val todayPosition = if (activeRota == null) null
    else AlarmScheduler.getShiftPositionForDate(today, activeRota, positions)

    val todayShiftType = if (activeRota == null) null
    else if (todayOverrides?.customShiftType != null) todayOverrides.customShiftType
    else todayPosition?.shiftType

    val activeShift = todayShiftType ?: ShiftType.OFF

    val containerColor = when (activeShift) {
        ShiftType.MORNING -> ShiftMorningContainer
        ShiftType.AFTERNOON -> ShiftAfternoonContainer
        ShiftType.NIGHT -> ShiftNightContainer
        ShiftType.OFF -> ShiftOffContainer
    }

    val contentColor = when (activeShift) {
        ShiftType.MORNING -> ShiftMorning
        ShiftType.AFTERNOON -> ShiftAfternoon
        ShiftType.NIGHT -> ShiftNight
        ShiftType.OFF -> ShiftOff
    }

    val timingText = if (todayOverrides?.customShiftType != null) {
        val ovrShift = todayOverrides.customShiftType
        when (ovrShift) {
            ShiftType.MORNING -> "06:00 - 14:00"
            ShiftType.AFTERNOON -> "14:00 - 22:00"
            ShiftType.NIGHT -> "22:00 - 06:00"
            ShiftType.OFF -> "Off Duty"
        }
    } else if (todayPosition != null && todayPosition.shiftType != ShiftType.OFF) {
        "${todayPosition.startTime} - ${todayPosition.endTime}"
    } else {
        "Off Duty"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (activeRota != null) "Active: ${activeRota.name}" else "No active rota",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(contentColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = activeShift.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color.White
                    )
                }
            }

            // High Density Big Clock Shift Timing matching the Tailwind spec
            Column {
                Text(
                    text = timingText,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 32.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = contentColor
                )
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alarm status",
                        tint = contentColor,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 8.dp)
                    )

                    Column {
                        Text(
                            text = "Next Alarm Timer",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = contentColor,
                            modifier = Modifier.testTag("countdown_timer")
                        )
                    }
                }

                // Quick actions button for customization
                Button(
                    onClick = onModifyTodayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = contentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Modify Today", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            if (todayOverrides?.isAlarmDisabled == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Silenced",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Alarms explicitly silenced for today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarGridSection(
    activeRota: Rota,
    positions: List<ShiftPosition>,
    overrides: List<OverrideDay>,
    onDateClick: (LocalDate) -> Unit
) {
    // Show 35 days (5 weeks) starting from base start day of current week (Monday)
    val today = LocalDate.now()
    val curDayOfWeek = today.dayOfWeek.value // Mon=1 ... Sun=7
    val startOfThisWeek = today.minusDays((curDayOfWeek - 1).toLong())

    // Render header
    val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // Week days Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of days
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (w in 0..4) { // 5 weeks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (d in 0..6) { // 7 days
                        val dayOffset = (w * 7) + d
                        val cellDate = startOfThisWeek.plusDays(dayOffset.toLong())

                        // Calculate shift and override states
                        val cellOverrides = overrides.firstOrNull { it.dateString == cellDate.toString() }
                        val computedType = if (cellOverrides?.customShiftType != null) {
                            cellOverrides.customShiftType
                        } else {
                            AlarmScheduler.getShiftPositionForDate(cellDate, activeRota, positions)?.shiftType
                        }

                        val shiftType = computedType ?: ShiftType.OFF

                        val cellBg = when (shiftType) {
                            ShiftType.MORNING -> ShiftMorningContainer
                            ShiftType.AFTERNOON -> ShiftAfternoonContainer
                            ShiftType.NIGHT -> ShiftNightContainer
                            ShiftType.OFF -> ShiftOffContainer
                        }

                        val cellBorderColor = when (shiftType) {
                            ShiftType.MORNING -> ShiftMorningBorder
                            ShiftType.AFTERNOON -> ShiftAfternoonBorder
                            ShiftType.NIGHT -> ShiftNightBorder
                            ShiftType.OFF -> ShiftOffBorder
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cellBg)
                                .border(
                                    width = if (cellDate == today) 2.dp else 1.dp,
                                    color = if (cellDate == today) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onDateClick(cellDate) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = cellDate.dayOfMonth.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (cellDate == today) FontWeight.Bold else FontWeight.Medium,
                                        color = when (shiftType) {
                                            ShiftType.MORNING -> ShiftMorning
                                            ShiftType.AFTERNOON -> ShiftAfternoon
                                            ShiftType.NIGHT -> ShiftNight
                                            ShiftType.OFF -> ShiftOff
                                        }
                                    )

                                    if (cellOverrides?.isAlarmDisabled == true || cellOverrides?.customShiftType != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (cellOverrides.isAlarmDisabled) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.secondary
                                                    )
                                            )
                                        }
                                    }
                                }

                                // Bottom thick border matching High Density theme (border-b-4 border-[color])
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(cellBorderColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingAlarmRow(
    alarm: AlarmScheduler.ScheduledAlarmInfo
) {
    val date = Instant.ofEpochMilli(alarm.epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val time = Instant.ofEpochMilli(alarm.epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()

    val formattedDate = date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    val formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    val color = when (alarm.shiftType.uppercase()) {
        "MORNING" -> ShiftMorning
        "AFTERNOON" -> ShiftAfternoon
        "NIGHT" -> ShiftNight
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor = when (alarm.shiftType.uppercase()) {
        "MORNING" -> ShiftMorningContainer
        "AFTERNOON" -> ShiftAfternoonContainer
        "NIGHT" -> ShiftNightContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upcoming_alarm_row"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Upcoming clock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "$formattedDate at $formattedTime",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${alarm.shiftType} Shift - ${alarm.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = alarm.sound.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
        }
    }
}

@Composable
fun DayModifierDialog(
    date: LocalDate,
    activeRota: Rota?,
    positions: List<ShiftPosition>,
    overrides: List<OverrideDay>,
    onDismiss: () -> Unit,
    onSave: (OverrideDay, Boolean) -> Unit
) {
    val existingOverride = overrides.firstOrNull { it.dateString == date.toString() }

    var selectedShiftType by remember {
        mutableStateOf(
            existingOverride?.customShiftType ?: AlarmScheduler.getShiftPositionForDate(date, activeRota!!, positions)?.shiftType ?: ShiftType.OFF
        )
    }

    var isAlarmDisabled by remember {
        mutableStateOf(existingOverride?.isAlarmDisabled ?: false)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit ${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Assign a swap shift category or silence alarms for this day specifically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Shift assignment options
                Text(
                    text = "Assign Duty:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShiftType.values().forEach { type ->
                        val isSel = selectedShiftType == type
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSel) {
                                        when (type) {
                                            ShiftType.MORNING -> ShiftMorningContainer
                                            ShiftType.AFTERNOON -> ShiftAfternoonContainer
                                            ShiftType.NIGHT -> ShiftNightContainer
                                            ShiftType.OFF -> ShiftOffContainer
                                        }
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { selectedShiftType = type }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedShiftType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSel) {
                                    when (type) {
                                        ShiftType.MORNING -> ShiftMorning
                                        ShiftType.AFTERNOON -> ShiftAfternoon
                                        ShiftType.NIGHT -> ShiftNight
                                        ShiftType.OFF -> ShiftOff
                                    }
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Alarm silencing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Silence Alarms",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Prevent alarms from firing on this date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isAlarmDisabled,
                        onCheckedChange = { isAlarmDisabled = it }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { onSave(OverrideDay(dateString = date.toString()), true) }
                    ) {
                        Text("Reset Day", color = MaterialTheme.colorScheme.error)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onSave(
                                    OverrideDay(
                                        dateString = date.toString(),
                                        isAlarmDisabled = isAlarmDisabled,
                                        customShiftType = selectedShiftType
                                    ),
                                    false
                                )
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
