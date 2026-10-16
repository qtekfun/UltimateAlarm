package com.qtekfun.ultimatealarm.feature.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
private fun rememberNow(tickMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(tickMs)
        }
    }
    return now
}

/** RF-E2: several timers at once, presets, custom durations with a label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timers by ClockStore.get(context).timers.collectAsStateWithLifecycle()
    val now = rememberNow(TIMER_TICK_MS)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.timer_presets), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Timers.PRESETS_MINUTES.split(',').map { it.toLong() }.forEach { minutes ->
                AssistChip(onClick = { Timers.add(context, minutes * MINUTE_MS) }, label = { Text(stringResource(R.string.timer_minutes, minutes.toInt())) })
            }
        }
        CustomTimer { duration, label -> Timers.add(context, duration, label) }
        timers.sortedBy { it.id }.forEach { timer -> TimerCard(timer, now) }
    }
}

@Composable
private fun CustomTimer(onAdd: (Long, String) -> Unit) {
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val total = ((hours.toLongOrNull() ?: 0) * 3_600 + (minutes.toLongOrNull() ?: 0) * 60 + (seconds.toLongOrNull() ?: 0)) * 1_000
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(hours, stringResource(R.string.timer_hours)) { hours = it }
                NumberField(minutes, stringResource(R.string.timer_min)) { minutes = it }
                NumberField(seconds, stringResource(R.string.timer_sec)) { seconds = it }
            }
            OutlinedTextField(value = label, onValueChange = {
                label = it
            }, label = { Text(stringResource(R.string.timer_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                onAdd(total, label.trim())
                hours = ""
                minutes = ""
                seconds = ""
                label = ""
            }, enabled = total > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.timer_start)) }
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(88.dp)
    )
}

@Composable
private fun TimerCard(timer: TimerState, now: Long) {
    val context = LocalContext.current
    val remaining = timer.remaining(now)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (timer.label.isNotBlank()) Text(timer.label, style = MaterialTheme.typography.titleMedium)
                    Text(formatDuration(remaining), fontSize = 44.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = {
                    Timers.delete(context, timer.id)
                }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.timer_delete)) }
            }
            LinearProgressIndicator(progress = {
                if (timer.durationMs >
                    0
                ) {
                    1f - remaining.toFloat() / timer.durationMs
                } else {
                    0f
                }
            }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.running) {
                    FilledTonalButton(onClick = { Timers.pause(context, timer.id) }) { Text(stringResource(R.string.timer_pause)) }
                } else if (!timer.finished) {
                    Button(onClick = { Timers.resume(context, timer.id) }) { Text(stringResource(R.string.timer_resume)) }
                }
                OutlinedButton(onClick = { Timers.plusMinute(context, timer.id) }) { Text(stringResource(R.string.timer_plus_minute)) }
                OutlinedButton(onClick = { Timers.reset(context, timer.id) }) { Text(stringResource(R.string.timer_reset)) }
            }
        }
    }
}

/** RF-E3: stopwatch with laps; keeps running with the app closed (state persisted, notification shows the time). */
@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by ClockStore.get(context).stopwatch.collectAsStateWithLifecycle()
    val now = rememberNow(if (state.running) STOPWATCH_TICK_MS else IDLE_TICK_MS)
    val parts = clockParts(state.elapsed(now))
    val h = parts[0]
    val m = parts[1]
    val s = parts[2]
    val cs = parts[3]
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            (if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 24.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { Stopwatch.toggle(context) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(if (state.running) R.string.timer_pause else R.string.stopwatch_start))
            }
            if (state.running) {
                FilledTonalButton(onClick = {
                    Stopwatch.lap(context)
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.stopwatch_lap)) }
            } else if (state.accumulatedMs > 0) {
                OutlinedButton(onClick = { Stopwatch.reset(context) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.timer_reset)) }
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.lapDurations().withIndex().reversed().forEach { (index, lap) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.stopwatch_lap_n, index + 1), Modifier.weight(1f))
                    Text(formatDuration(lap - MS_ROUND_DOWN), fontFamily = FontFamily.Monospace)
                    Text(
                        formatDuration(state.laps[index] - MS_ROUND_DOWN),
                        Modifier.padding(start = 16.dp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val TIMER_TICK_MS = 250L
private const val STOPWATCH_TICK_MS = 30L
private const val IDLE_TICK_MS = 1_000L

/** [formatDuration] rounds up for countdowns; elapsed times round down. */
private const val MS_ROUND_DOWN = 999L
