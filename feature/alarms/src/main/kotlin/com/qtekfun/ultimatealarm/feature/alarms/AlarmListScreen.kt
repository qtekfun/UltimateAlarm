package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZonedDateTime

/** The alarm list (RF-A2): sorted by next occurrence, each row with its status, "skip tomorrow" and the switch. */
@Composable
fun AlarmListScreen(
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: (AlarmEvent) -> Unit = {},
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { event ->
            snackbarMessage(event)
            val text = when (event) {
                is AlarmEvent.RingsIn -> event.duration?.let { resources.ringsInMessage(it) } ?: resources.getString(R.string.will_not_ring)
                is AlarmEvent.Deleted -> resources.getString(R.string.alarm_deleted)
                is AlarmEvent.Skipped -> resources.getString(R.string.alarm_skipped, formatDate(event.date))
            }
            val undo = if (event is AlarmEvent.RingsIn) null else resources.getString(R.string.undo)
            val result = snackbar.showSnackbar(text, actionLabel = undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                when (event) {
                    is AlarmEvent.Deleted -> viewModel.restore(event.alarm)
                    is AlarmEvent.Skipped -> viewModel.unskip(event.alarmId, event.date)
                    is AlarmEvent.RingsIn -> Unit
                }
            }
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0) },
                modifier = Modifier.testTag(TAG_ADD_ALARM),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_alarm)) }
            )
        }
    ) { padding ->
        if (state.loaded && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_alarms), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom =
                        padding.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.rows, key = { it.alarm.id }) { row ->
                    AlarmRowCard(
                        row = row,
                        now = state.now,
                        onClick = { onEdit(row.alarm.id) },
                        onToggle = { viewModel.setEnabled(row.alarm.id, it) },
                        onSkip = { viewModel.skip(row) },
                        onUnskip = { date -> viewModel.unskip(row.alarm.id, date) },
                        onDuplicate = { viewModel.duplicate(row.alarm.id) },
                        onDelete = { viewModel.delete(row.alarm.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmRowCard(
    row: AlarmRow,
    now: ZonedDateTime,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onUnskip: (java.time.LocalDate) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val alarm = row.alarm
    val enabled = alarm.enabled
    val colors = if (enabled) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = colors) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        timeText(alarm.time),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val subtitle = listOf(alarm.label, scheduleText(alarm)).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RowMenu(onDuplicate, onDelete)
                val switchLabel = stringResource(R.string.alarm_switch, timeText(alarm.time))
                Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = switchLabel })
            }
            StatusLine(row, now)
            if (enabled && (row.skipTarget != null || row.activeSkip != null)) {
                val active = row.activeSkip
                OutlinedButton(onClick = { if (active != null) onUnskip(active) else onSkip() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (active != null) stringResource(R.string.undo_skip) else stringResource(R.string.skip_next, formatDate(row.skipTarget!!)))
                }
            }
        }
    }
}

@Composable
private fun StatusLine(row: AlarmRow, now: ZonedDateTime) {
    val alarm = row.alarm
    val lines = buildList {
        when {
            alarm.missed -> add(stringResource(R.string.status_missed))
            !alarm.enabled -> add(stringResource(R.string.status_off))
            row.next == null -> add(stringResource(R.string.will_not_ring))
            else -> add(ringsInText(row.ringsIn(now)!!))
        }
        if (alarm.enabled) row.suppression?.let { add(suppressionText(it)) }
    }.filter { it.isNotBlank() }
    lines.forEachIndexed { index, line ->
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = if (index == 0 && alarm.enabled && !alarm.missed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RowMenu(onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = {
                open = false
                onDuplicate()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                open = false
                onDelete()
            })
        }
    }
}

const val TAG_ADD_ALARM = "add_alarm"

/** Snackbar text for RF-A3, outside composition. */
fun android.content.res.Resources.ringsInMessage(duration: java.time.Duration): String {
    val (hours, minutes) = hoursAndMinutes(duration)
    return getString(R.string.snackbar_rings_in, hours.toInt(), minutes.toInt())
}
