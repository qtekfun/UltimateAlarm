package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** RF-P2: presets 4×4, 5×2, 6×3, 2×2, alternate weeks, or a custom cycle, with the day the cycle starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftEditor(cycle: Schedule.Cycle, onChange: (Schedule.Cycle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShiftPreset.entries.forEach { preset ->
                AssistChip(
                    onClick = { onChange(ScheduleTools.shift(preset, cycle.anchor)) },
                    label = { Text(stringResource(R.string.shift_preset, preset.on, preset.off)) }
                )
            }
            AssistChip(
                onClick = { onChange(ScheduleTools.alternateWeeks(Alarm.WORKDAYS, cycle.anchor)) },
                label = { Text(stringResource(R.string.shift_alternate_weeks)) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.shift_length, cycle.mask.size))
            IconButton(
                onClick = { onChange(cycle.copy(mask = ScheduleTools.resize(cycle.mask, cycle.mask.size - 1))) },
                enabled =
                    cycle.mask.size > ScheduleTools.MIN_CYCLE
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.shift_shorter))
            }
            IconButton(
                onClick = { onChange(cycle.copy(mask = ScheduleTools.resize(cycle.mask, cycle.mask.size + 1))) },
                enabled =
                    cycle.mask.size < ScheduleTools.MAX_CYCLE
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shift_longer))
            }
        }
        Text(stringResource(R.string.shift_days_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cycle.mask.forEachIndexed { index, on ->
                val day = cycle.anchor.plusDays(index.toLong())
                FilterChip(
                    selected = on,
                    onClick = { onChange(cycle.copy(mask = cycle.mask.toMutableList().also { it[index] = !on })) },
                    label = { Text(stringResource(R.string.shift_day, index + 1, formatDay(day.dayOfWeek))) }
                )
            }
        }
        AnchorPicker(cycle.anchor) { onChange(cycle.copy(anchor = it)) }
        val upcoming = generateSequence(LocalDate.now()) { it.plusDays(1) }.take(PREVIEW_DAYS).filter { cycle.isScheduledOn(it) }.take(PREVIEW_RINGS).toList()
        Text(
            stringResource(R.string.shift_preview, upcoming.joinToString(", ") { formatDate(it) }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnchorPicker(anchor: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.shift_anchor, formatLongDate(anchor))) }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = anchor.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}

private const val PREVIEW_DAYS = 60
private const val PREVIEW_RINGS = 6
