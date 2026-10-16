package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatealarm.core.designsystem.card
import com.qtekfun.ultimatealarm.core.designsystem.timeStyle
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.OffReason
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle as JavaTextStyle

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
        containerColor = MaterialTheme.colorScheme.background,
        // The main screen's own scaffold already leaves room for the system bars.
        contentWindowInsets = WindowInsets(0),
        // Above the floating navigation of the main screen.
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 88.dp)) }
    ) { padding ->
        if (state.loaded && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_alarms), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val next = state.rows.firstOrNull { it.alarm.enabled && it.next != null }
            val notices = state.rows.filter { it.alarm.enabled && it.suppression?.let { s -> s.reason in NOTICE_REASONS } == true }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                next?.let { row -> item(key = "next") { NextAlarmCard(row, state.now, onClick = { onEdit(row.alarm.id) }) } }
                items(notices, key = { "notice-${it.alarm.id}" }) { row -> NoticeCard(row, onClick = { onEdit(row.alarm.id) }) }
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

/** D-19: the next alarm as the hero of the list, on the accent colour. */
@Composable
private fun NextAlarmCard(row: AlarmRow, now: ZonedDateTime, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val at = row.next!!.at
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.primary)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(colors.onPrimary.copy(alpha = 0.14f))
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp)) {
            Text(
                stringResource(R.string.next_alarm, dayText(at.toLocalDate(), now.toLocalDate())).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary
            )
            Text(timeText(row.alarm.time), style = timeStyle(76), color = colors.onPrimary, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ringsInText(row.ringsIn(now)!!), style = MaterialTheme.typography.titleMedium, color = colors.onPrimary, modifier = Modifier.weight(1f))
                if (row.alarm.label.isNotBlank()) {
                    Text(
                        row.alarm.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimary,
                        maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(colors.onPrimary.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** A day an enabled alarm will not ring (holiday, vacation, skip, exception), above the list. */
@Composable
private fun NoticeCard(row: AlarmRow, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val suppression = row.suppression!!
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(colors.card), contentAlignment = Alignment.Center) {
            Icon(noticeIcon(suppression.reason), contentDescription = null, tint = colors.secondary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(suppressionText(suppression), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            val alarm = listOf(timeText(row.alarm.time), row.alarm.label).filter { it.isNotBlank() }.joinToString(" · ")
            Text(stringResource(R.string.notice_alarm, alarm), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

private fun noticeIcon(reason: OffReason): ImageVector = when (reason) {
    OffReason.VACATION -> Icons.Outlined.BeachAccess
    OffReason.SKIPPED -> Icons.Outlined.SkipNext
    else -> Icons.Outlined.Event
}

private val NOTICE_REASONS = setOf(OffReason.HOLIDAY, OffReason.VACATION, OffReason.SKIPPED, OffReason.EXCEPTION)

@Composable
private fun dayText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow, formatDay(date.dayOfWeek, JavaTextStyle.FULL))
    else -> formatDate(date)
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
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(timeText(alarm.time), style = timeStyle(44), color = if (enabled) colors.onSurface else colors.outline)
                val subtitle = listOf(alarm.label, scheduleText(alarm)).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) colors.onSurfaceVariant else colors.outline,
                    modifier = Modifier.padding(top = 6.dp)
                )
                StatusLine(row, now)
            }
            RowMenu(onDuplicate, onDelete)
            val switchLabel = stringResource(R.string.alarm_switch, timeText(alarm.time))
            Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = switchLabel })
        }
        (alarm.schedule as? Schedule.Weekly)?.takeIf { alarm.kind != AlarmKind.ONE_SHOT }?.let { DayPills(it.days, enabled) }
        if (enabled && (row.skipTarget != null || row.activeSkip != null)) {
            val active = row.activeSkip
            FilledTonalButton(onClick = { if (active != null) onUnskip(active) else onSkip() }) {
                Text(if (active != null) stringResource(R.string.undo_skip) else stringResource(R.string.skip_next, formatDate(row.skipTarget!!)))
            }
        }
    }
}

/** Monday-first day initials; the ringing days on the accent's soft tone. */
@Composable
private fun DayPills(days: Set<DayOfWeek>, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val description = days.sorted().joinToString(", ") { formatDay(it, JavaTextStyle.FULL) }
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            val background = when {
                on && enabled -> colors.primaryContainer
                else -> colors.surfaceVariant
            }
            val foreground = if (on && enabled) colors.onPrimaryContainer else colors.outline
            Box(Modifier.size(34.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                Text(formatDay(day, JavaTextStyle.NARROW), style = MaterialTheme.typography.labelMedium, color = foreground)
            }
        }
    }
}

@Composable
private fun StatusLine(row: AlarmRow, now: ZonedDateTime) {
    val alarm = row.alarm
    val line = when {
        alarm.missed -> stringResource(R.string.status_missed)
        !alarm.enabled -> stringResource(R.string.status_off)
        row.next == null -> stringResource(R.string.will_not_ring)
        else -> ringsInText(row.ringsIn(now)!!)
    }
    Text(
        line,
        style = MaterialTheme.typography.bodyMedium,
        color = if (alarm.enabled && !alarm.missed && row.next != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
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
