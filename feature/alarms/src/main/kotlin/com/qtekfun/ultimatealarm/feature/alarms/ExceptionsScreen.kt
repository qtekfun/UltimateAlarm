package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.scheduling.DayVerdict
import com.qtekfun.ultimatealarm.core.scheduling.OffReason
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExceptionsState(val alarm: Alarm? = null, val rules: RuleSet = RuleSet())

@HiltViewModel
class ExceptionsViewModel @Inject constructor(savedState: SavedStateHandle, private val repository: AlarmRepository) : ViewModel() {
    private val alarmId: Long = savedState.get<Long>(AlarmEditorViewModel.ARG_ALARM_ID) ?: 0L

    val state: StateFlow<ExceptionsState> = combine(
        repository.observeAlarms().map { list ->
            list.firstOrNull { it.id == alarmId }
        },
        repository.observeRules()
    ) { alarm, rules ->
        ExceptionsState(alarm, rules.copy(holidays = repository.holidayCalendar()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExceptionsState())

    /** RF-P3: según patrón → forzar sonar → forzar no sonar. */
    fun toggle(date: LocalDate) = viewModelScope.launch {
        val current = state.value.rules.overrides.lastOrNull { it.alarmId == alarmId && it.date == date }?.mode
        repository.setOverride(alarmId, date, ScheduleTools.nextOverride(current))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** RF-P3: monthly calendar of one alarm; each day shows whether it rings and why not; tapping cycles the exception. */
@Composable
fun ExceptionsScreen(modifier: Modifier = Modifier, viewModel: ExceptionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val alarm = state.alarm ?: return
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val current = YearMonth.parse(month)
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.exceptions_help), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = current.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.exceptions_previous))
            }
            Text(
                current.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = current.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.exceptions_next))
            }
        }
        Row {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        ScheduleTools.monthGrid(current).forEach { week ->
            Row {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, alarm, state.rules) { viewModel.toggle(date) }
                    }
                }
            }
        }
        Legend()
    }
}

@Composable
private fun DayCell(date: LocalDate, alarm: Alarm, rules: RuleSet, onClick: () -> Unit) {
    val verdict = SchedulingEngine.explain(alarm.spec, date, rules)
    val override = rules.overrides.lastOrNull { it.alarmId == alarm.id && it.date == date }?.mode
    val rings = verdict == DayVerdict.Rings
    val background = when {
        override == OverrideMode.FORCE_ON -> MaterialTheme.colorScheme.primary
        override == OverrideMode.FORCE_OFF -> MaterialTheme.colorScheme.error
        rings -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        override == OverrideMode.FORCE_ON -> MaterialTheme.colorScheme.onPrimary
        override == OverrideMode.FORCE_OFF -> MaterialTheme.colorScheme.onError
        rings -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = cellDescription(date, verdict, override)
    Box(
        Modifier.size(44.dp).background(background, CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = foreground,
            fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun cellDescription(date: LocalDate, verdict: DayVerdict, override: OverrideMode?): String {
    val day = formatLongDate(date)
    val state = when {
        override == OverrideMode.FORCE_ON -> stringResource(R.string.exceptions_forced_on)
        override == OverrideMode.FORCE_OFF -> stringResource(R.string.exceptions_forced_off)
        verdict == DayVerdict.Rings -> stringResource(R.string.exceptions_rings)
        (verdict as? DayVerdict.Off)?.reason == OffReason.HOLIDAY -> stringResource(R.string.exceptions_holiday)
        (verdict as? DayVerdict.Off)?.reason == OffReason.VACATION -> stringResource(R.string.exceptions_vacation)
        (verdict as? DayVerdict.Off)?.reason == OffReason.SKIPPED -> stringResource(R.string.exceptions_skipped)
        else -> stringResource(R.string.exceptions_silent)
    }
    return "$day: $state"
}

@Composable
private fun Legend() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow(MaterialTheme.colorScheme.primaryContainer, stringResource(R.string.exceptions_rings))
        LegendRow(MaterialTheme.colorScheme.primary, stringResource(R.string.exceptions_forced_on))
        LegendRow(MaterialTheme.colorScheme.error, stringResource(R.string.exceptions_forced_off))
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(16.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
