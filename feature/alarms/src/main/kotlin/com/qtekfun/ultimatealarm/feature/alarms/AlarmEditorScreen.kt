package com.qtekfun.ultimatealarm.feature.alarms

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen alarm editor (RF-A1) with collapsible sections (02-diseno §10). Sound choice arrives in Phase 5, challenges
 * in Phase 8 and shift patterns in Phase 7; their stored values are kept untouched here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    onDone: (saved: Duration?, deleted: Alarm?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val draft = state.draft
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.new_alarm else R.string.edit_alarm)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = { scope.launch { onDone(null, viewModel.delete()) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
                TextButton(
                    onClick = { scope.launch { onDone(viewModel.save(), null) } },
                    enabled = state.loaded,
                    modifier = Modifier.testTag(TAG_SAVE_ALARM)
                ) { Text(stringResource(R.string.save)) }
            }
        )
        if (!state.loaded) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeSection(draft.time, viewModel::setTime)
            OutlinedTextField(
                value = draft.label,
                onValueChange = { label -> viewModel.edit { it.copy(label = label) } },
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            RepeatSection(draft, viewModel)
            Section(stringResource(R.string.section_ringing)) {
                SliderRow(stringResource(R.string.max_volume, draft.maxVolume), draft.maxVolume.toFloat(), 1f..100f) { v ->
                    viewModel.edit { it.copy(maxVolume = v.roundToInt()) }
                }
                SliderRow(stringResource(R.string.ramp_seconds, draft.rampSeconds), draft.rampSeconds.toFloat(), 0f..300f, steps = 59) { v ->
                    viewModel.edit { it.copy(rampSeconds = v.roundToInt()) }
                }
                SwitchRow(stringResource(R.string.vibrate), draft.vibrate) { on -> viewModel.edit { it.copy(vibrate = on) } }
                Text(stringResource(R.string.sound_default), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section(stringResource(R.string.section_snooze)) {
                SliderRow(stringResource(R.string.snooze_minutes, draft.snoozeMinutes), draft.snoozeMinutes.toFloat(), 1f..30f, steps = 28) { v ->
                    viewModel.edit { it.copy(snoozeMinutes = v.roundToInt()) }
                }
                val unlimited = draft.maxSnoozes == Alarm.UNLIMITED_SNOOZES
                SwitchRow(stringResource(R.string.snooze_unlimited), unlimited) { on ->
                    viewModel.edit { it.copy(maxSnoozes = if (on) Alarm.UNLIMITED_SNOOZES else DEFAULT_MAX_SNOOZES) }
                }
                if (!unlimited) {
                    SliderRow(stringResource(R.string.max_snoozes, draft.maxSnoozes), draft.maxSnoozes.toFloat(), 0f..10f, steps = 9) { v ->
                        viewModel.edit { it.copy(maxSnoozes = v.roundToInt()) }
                    }
                }
            }
            Section(stringResource(R.string.section_backup)) {
                val backup = draft.backupAfterMinutes
                SwitchRow(stringResource(R.string.backup_enabled), backup != null) { on ->
                    viewModel.edit { it.copy(backupAfterMinutes = if (on) DEFAULT_BACKUP_MINUTES else null) }
                }
                if (backup != null) {
                    SliderRow(stringResource(R.string.backup_minutes, backup), backup.toFloat(), 1f..30f, steps = 28) { v ->
                        viewModel.edit { it.copy(backupAfterMinutes = v.roundToInt()) }
                    }
                }
                SwitchRow(stringResource(R.string.pre_notify), draft.preNotifyMinutes != null) { on ->
                    viewModel.edit { it.copy(preNotifyMinutes = if (on) PRE_NOTIFY_MINUTES else null) }
                }
            }
            Button(onClick = { scope.launch { onDone(viewModel.save(), null) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(time: LocalTime, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(context))
    LaunchedEffect(picker) {
        snapshotFlow { LocalTime.of(picker.hour, picker.minute) }.collect(onTime)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TimePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(draft: Alarm, viewModel: AlarmEditorViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val kinds = listOf(AlarmKind.RECURRING to R.string.kind_recurring, AlarmKind.ONE_SHOT to R.string.kind_one_shot)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = draft.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size)
                    ) { Text(stringResource(label)) }
                }
            }
            if (draft.kind == AlarmKind.RECURRING) {
                val schedule = draft.schedule
                if (schedule is Schedule.Weekly) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in schedule.days,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(formatDay(day, TextStyle.SHORT)) }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { viewModel.setDays(Alarm.WORKDAYS) }, label = { Text(stringResource(R.string.schedule_weekdays)) })
                        AssistChip(onClick = { viewModel.setDays(WEEKEND) }, label = { Text(stringResource(R.string.schedule_weekend)) })
                        AssistChip(onClick = { viewModel.setDays(DayOfWeek.entries.toSet()) }, label = { Text(stringResource(R.string.schedule_every_day)) })
                    }
                } else {
                    Text(scheduleText(draft), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                OneShotDate(draft.oneShotDate, viewModel::setOneShotDate)
            }
            SwitchRow(stringResource(R.string.respect_holidays), draft.respectHolidays) { on -> viewModel.edit { it.copy(respectHolidays = on) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneShotDate(date: LocalDate?, onDate: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Text(
        if (date == null) stringResource(R.string.schedule_next_time) else stringResource(R.string.schedule_once, formatLongDate(date)),
        style = MaterialTheme.typography.bodyLarge
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.pick_date)) }
        if (date != null) TextButton(onClick = { onDate(null) }) { Text(stringResource(R.string.schedule_next_time)) }
    }
    if (picking) {
        val today = LocalDate.now()
        val initial = (date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (open) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

const val TAG_SAVE_ALARM = "save_alarm"

private const val DEFAULT_MAX_SNOOZES = 3
private const val DEFAULT_BACKUP_MINUTES = 5
private const val PRE_NOTIFY_MINUTES = 60
