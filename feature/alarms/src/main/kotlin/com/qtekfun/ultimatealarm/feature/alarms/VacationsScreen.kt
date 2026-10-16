package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VacationsState(val current: List<Vacation> = emptyList(), val past: List<Vacation> = emptyList(), val alarms: List<Alarm> = emptyList())

@HiltViewModel
class VacationsViewModel @Inject constructor(private val repository: AlarmRepository) : ViewModel() {
    /** RF-P4: past ranges are archived (listed apart) automatically. */
    val state: StateFlow<VacationsState> = combine(repository.observeVacations(), repository.observeAlarms()) { vacations, alarms ->
        val today = LocalDate.now()
        val (past, current) = vacations.partition { it.end.isBefore(today) }
        VacationsState(current.sortedBy { it.start }, past.sortedByDescending { it.start }, alarms.filter { it.kind == AlarmKind.RECURRING })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VacationsState())

    fun save(vacation: Vacation) = viewModelScope.launch { repository.saveVacation(vacation) }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteVacation(id) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** RF-P4: date ranges that pause every alarm or a selection; editable, deletable; past ones archived. */
@Composable
fun VacationsScreen(modifier: Modifier = Modifier, viewModel: VacationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPast by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = 0 },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.vacation_add)) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.vacation_help), style = MaterialTheme.typography.bodyMedium)
            if (state.current.isEmpty()) Text(stringResource(R.string.vacation_none), style = MaterialTheme.typography.bodyLarge)
            state.current.forEach { VacationCard(it, state.alarms, onEdit = { editing = it.id }, onDelete = { viewModel.delete(it.id) }) }
            if (state.past.isNotEmpty()) {
                TextButton(onClick = { showPast = !showPast }) { Text(stringResource(R.string.vacation_past, state.past.size)) }
                if (showPast) state.past.forEach { VacationCard(it, state.alarms, onEdit = null, onDelete = { viewModel.delete(it.id) }) }
            }
            Box(Modifier.heightIn(min = 72.dp))
        }
    }
    editing?.let { id ->
        VacationDialog(
            initial = state.current.firstOrNull { it.id == id },
            alarms = state.alarms,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun VacationCard(vacation: Vacation, alarms: List<Alarm>, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().let { if (onEdit != null) it.clickable(onClick = onEdit) else it }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.vacation_range, formatLongDate(vacation.start), formatLongDate(vacation.end)),
                    style = MaterialTheme.typography.titleMedium
                )
                val context = LocalContext.current
                val none = stringResource(R.string.vacation_no_alarms)
                val everyAlarm = stringResource(R.string.vacation_all)
                val scope = vacation.alarmIds?.let { ids ->
                    alarms.filter { it.id in ids }.joinToString(", ") { alarm -> alarm.label.ifBlank { formatTime(context, alarm.time) } }.ifBlank { none }
                } ?: everyAlarm
                Text(scope, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationDialog(initial: Vacation?, alarms: List<Alarm>, onSave: (Vacation) -> Unit, onDismiss: () -> Unit) {
    val range = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.let(::millis),
        initialSelectedEndDateMillis = initial?.end?.let(::millis)
    )
    var all by rememberSaveable { mutableStateOf(initial?.alarmIds == null) }
    var selected by rememberSaveable { mutableStateOf(initial?.alarmIds?.toList().orEmpty()) }
    var pickingDates by rememberSaveable { mutableStateOf(initial == null) }
    val start = range.selectedStartDateMillis?.let(::date)
    val end = range.selectedEndDateMillis?.let(::date) ?: start

    if (pickingDates) {
        DatePickerDialog(
            onDismissRequest = { if (start == null) onDismiss() else pickingDates = false },
            confirmButton = { TextButton(enabled = start != null, onClick = { pickingDates = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        ) { DateRangePicker(state = range, modifier = Modifier.heightIn(max = 520.dp)) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.vacation_add else R.string.vacation_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { pickingDates = true }) {
                    Text(
                        if (start != null &&
                            end != null
                        ) {
                            stringResource(R.string.vacation_range, formatLongDate(start), formatLongDate(end))
                        } else {
                            stringResource(R.string.pick_date)
                        }
                    )
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vacation_all), Modifier.weight(1f))
                    Switch(checked = all, onCheckedChange = { all = it })
                }
                if (!all) {
                    alarms.forEach { alarm ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = alarm.id in selected, onCheckedChange = { on ->
                                selected = if (on) selected + alarm.id else selected - alarm.id
                            })
                            Text(listOf(timeText(alarm.time), alarm.label).filter { it.isNotBlank() }.joinToString(" · "))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = start != null && end != null, onClick = {
                onSave(Vacation(initial?.id ?: 0, start!!, end!!, if (all) null else selected.toSet()))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun millis(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
