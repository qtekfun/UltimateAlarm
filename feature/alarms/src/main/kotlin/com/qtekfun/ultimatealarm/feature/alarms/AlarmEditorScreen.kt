package com.qtekfun.ultimatealarm.feature.alarms

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Snooze
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.designsystem.card
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.feature.sounds.SoundPickerDialog
import com.qtekfun.ultimatealarm.feature.sounds.SoundRef
import com.qtekfun.ultimatealarm.feature.sounds.soundName
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
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
    onExceptions: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlarmEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Navigation must run on the main thread whatever dispatcher the composition's scope uses (e.g. in tests).
    val scope = rememberCoroutineScope()
    val draft = state.draft
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.new_alarm else R.string.edit_alarm)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back)) } },
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = { scope.launch(Dispatchers.Main) { onDone(null, viewModel.delete()) } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
                Button(
                    onClick = { scope.launch(Dispatchers.Main) { onDone(viewModel.save(), null) } },
                    enabled = state.loaded,
                    modifier = Modifier.padding(end = 8.dp).testTag(TAG_SAVE_ALARM)
                ) { Text(stringResource(R.string.save)) }
            }
        )
        if (!state.loaded) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeWheels(draft.time, viewModel::setTime)
            TextField(
                value = draft.label,
                onValueChange = { label -> viewModel.edit { it.copy(label = label) } },
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.card,
                    unfocusedContainerColor = MaterialTheme.colorScheme.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            RepeatSection(draft, viewModel)
            if (!state.isNew && draft.kind == AlarmKind.RECURRING) {
                TextButton(onClick = { onExceptions(draft.id) }) { Text(stringResource(R.string.exceptions_open)) }
            }
            Section(
                stringResource(R.string.section_ringing),
                Icons.Outlined.MusicNote,
                IconTone.ACCENT,
                listOf(
                    if (draft.sound ==
                        null
                    ) {
                        soundName(null)
                    } else {
                        soundName(SoundRef.decode(draft.sound))
                    },
                    stringResource(R.string.ramp_short, draft.rampSeconds)
                )
                    .joinToString(" · ")
            ) {
                SliderRow(stringResource(R.string.max_volume, draft.maxVolume), draft.maxVolume.toFloat(), 1f..100f) { v ->
                    viewModel.edit { it.copy(maxVolume = v.roundToInt()) }
                }
                SliderRow(stringResource(R.string.ramp_seconds, draft.rampSeconds), draft.rampSeconds.toFloat(), 0f..300f, steps = 59) { v ->
                    viewModel.edit { it.copy(rampSeconds = v.roundToInt()) }
                }
                SwitchRow(stringResource(R.string.vibrate), draft.vibrate) { on -> viewModel.edit { it.copy(vibrate = on) } }
                SoundRow(
                    title = stringResource(com.qtekfun.ultimatealarm.feature.sounds.R.string.sound_title),
                    current = SoundRef.decode(draft.sound),
                    backup = false,
                    draft = draft,
                    noneLabel = soundName(null)
                ) { ref -> viewModel.edit { it.copy(sound = ref?.encode()) } }
            }
            Section(
                stringResource(R.string.section_snooze),
                Icons.Outlined.Snooze,
                IconTone.SECONDARY,
                if (draft.maxSnoozes == Alarm.UNLIMITED_SNOOZES) {
                    stringResource(R.string.snooze_short_unlimited, draft.snoozeMinutes)
                } else {
                    stringResource(R.string.snooze_short, draft.snoozeMinutes, draft.maxSnoozes)
                }
            ) {
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
            Section(stringResource(R.string.section_challenge), Icons.Outlined.Extension, IconTone.TERTIARY, null) {
                ChallengeSection(draft.challenge) { encoded -> viewModel.edit { it.copy(challenge = encoded) } }
            }
            Section(stringResource(R.string.section_backup), Icons.Outlined.NotificationsActive, IconTone.TERTIARY, null) {
                val backup = draft.backupAfterMinutes
                SwitchRow(stringResource(R.string.backup_enabled), backup != null) { on ->
                    viewModel.edit { it.copy(backupAfterMinutes = if (on) DEFAULT_BACKUP_MINUTES else null) }
                }
                if (backup != null) {
                    SliderRow(stringResource(R.string.backup_minutes, backup), backup.toFloat(), 1f..30f, steps = 28) { v ->
                        viewModel.edit { it.copy(backupAfterMinutes = v.roundToInt()) }
                    }
                }
                if (backup != null) {
                    SoundRow(
                        title = stringResource(com.qtekfun.ultimatealarm.feature.sounds.R.string.sound_backup_title),
                        current = SoundRef.decode(draft.backupSound),
                        backup = true,
                        draft = draft,
                        noneLabel = stringResource(com.qtekfun.ultimatealarm.feature.sounds.R.string.sound_backup_global)
                    ) { ref -> viewModel.edit { it.copy(backupSound = ref?.encode()) } }
                }
                SwitchRow(stringResource(R.string.pre_notify), draft.preNotifyMinutes != null) { on ->
                    viewModel.edit { it.copy(preNotifyMinutes = if (on) PRE_NOTIFY_MINUTES else null) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(draft: Alarm, viewModel: AlarmEditorViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        run {
            val schedule = draft.schedule
            val mode = when {
                draft.kind == AlarmKind.ONE_SHOT -> RepeatMode.ONCE
                schedule is Schedule.Cycle -> RepeatMode.SHIFTS
                else -> RepeatMode.WEEKLY
            }
            PillChoice(
                options = listOf(
                    RepeatMode.WEEKLY to stringResource(R.string.repeat_weekly),
                    RepeatMode.SHIFTS to stringResource(R.string.repeat_shifts),
                    RepeatMode.ONCE to stringResource(R.string.kind_one_shot)
                ),
                selected = mode
            ) { picked ->
                when (picked) {
                    RepeatMode.ONCE -> viewModel.setKind(AlarmKind.ONE_SHOT)
                    RepeatMode.WEEKLY -> {
                        if (draft.kind != AlarmKind.RECURRING) viewModel.setKind(AlarmKind.RECURRING)
                        if (schedule !is Schedule.Weekly) viewModel.setDays(Alarm.WORKDAYS)
                    }
                    RepeatMode.SHIFTS -> {
                        if (draft.kind != AlarmKind.RECURRING) viewModel.setKind(AlarmKind.RECURRING)
                        if (schedule !is Schedule.Cycle) viewModel.setSchedule(ScheduleTools.shift(ShiftPreset.FOUR_FOUR, LocalDate.now()))
                    }
                }
            }
            if (draft.kind == AlarmKind.RECURRING) {
                when (schedule) {
                    is Schedule.Weekly -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { day -> DayToggle(day, day in schedule.days) { viewModel.toggleDay(day) } }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(onClick = { viewModel.setDays(Alarm.WORKDAYS) }, label = { Text(stringResource(R.string.schedule_weekdays)) })
                            AssistChip(onClick = { viewModel.setDays(WEEKEND) }, label = { Text(stringResource(R.string.schedule_weekend)) })
                            AssistChip(onClick = {
                                viewModel.setDays(DayOfWeek.entries.toSet())
                            }, label = { Text(stringResource(R.string.schedule_every_day)) })
                        }
                    }
                    is Schedule.Cycle -> ShiftEditor(schedule, viewModel::setSchedule)
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
private fun Section(title: String, icon: ImageVector, tone: IconTone, summary: String?, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.card)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(icon, tone)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

@Composable
private fun SoundRow(title: String, current: SoundRef?, backup: Boolean, draft: Alarm, noneLabel: String, onPick: (SoundRef?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text(if (current == null) noneLabel else soundName(current), color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        SoundPickerDialog(
            title = title,
            current = current,
            backup = backup,
            rampSeconds = draft.rampSeconds,
            maxVolume = draft.maxVolume,
            noneLabel = noneLabel,
            onPick = onPick,
            onDismiss = { open = false }
        )
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

private enum class RepeatMode { WEEKLY, SHIFTS, ONCE }

/** A three-way choice as one pill: the chosen option inverted (dark on light, light on dark). */
@Composable
private fun <T> PillChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceContainer).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.onSurface else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) colors.surface else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayToggle(day: DayOfWeek, on: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = formatDay(day, TextStyle.FULL)
    Box(
        Modifier.size(42.dp)
            .clip(CircleShape)
            .background(if (on) colors.primary else colors.surfaceVariant)
            .toggleable(value = on, role = Role.Checkbox) { onToggle() }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Text(formatDay(day, TextStyle.NARROW), style = MaterialTheme.typography.labelLarge, color = if (on) colors.onPrimary else colors.onSurfaceVariant)
    }
}

private enum class IconTone { ACCENT, SECONDARY, TERTIARY, NEUTRAL }

@Composable
private fun IconTile(icon: ImageVector, tone: IconTone) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (tone) {
        IconTone.ACCENT -> colors.primaryContainer to colors.onPrimaryContainer
        IconTone.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
        IconTone.TERTIARY -> colors.tertiaryContainer to colors.onTertiaryContainer
        IconTone.NEUTRAL -> colors.surfaceContainer to colors.onSurfaceVariant
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
    }
}
