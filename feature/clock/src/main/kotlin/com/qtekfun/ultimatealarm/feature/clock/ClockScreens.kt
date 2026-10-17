package com.qtekfun.ultimatealarm.feature.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatealarm.core.designsystem.card
import com.qtekfun.ultimatealarm.core.designsystem.isDark
import com.qtekfun.ultimatealarm.core.designsystem.timeStyle
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

/**
 * RF-E2 in the "Amanecer" style (D-19): running timers as cards with a ring that empties, and a keypad to set a new one
 * (digits fill from the right, quick presets start at once). With timers running the keypad folds into a button.
 */
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timers by ClockStore.get(context).timers.collectAsStateWithLifecycle()
    val now = rememberNow(TIMER_TICK_MS)
    var composing by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        timers.sortedBy { it.id }.forEach { timer -> TimerCard(timer, now) }
        if (composing || timers.isEmpty()) {
            TimerComposer(
                onStart = { duration, label ->
                    Timers.add(context, duration, label)
                    composing = false
                },
                onCancel = if (timers.isEmpty()) null else ({ composing = false })
            )
        } else {
            FilledTonalButton(onClick = { composing = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.timer_new), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TimerComposer(onStart: (Long, String) -> Unit, onCancel: (() -> Unit)?) {
    var digits by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val duration = TimerKeypad.durationMs(digits)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(colors.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EntryDisplay(digits)
        Presets(onPick = { minutes -> onStart(minutes * MINUTE_MS, label.trim()) })
        Keypad(onKey = { digits = TimerKeypad.type(digits, it) }, onDelete = { digits = TimerKeypad.delete(digits) })
        TextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.timer_label)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.keyTone,
                unfocusedContainerColor = colors.keyTone,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.timer_cancel)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onStart(duration, label.trim()) }, enabled = duration > 0, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.timer_start), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "00h 05m 00s": what has been typed in full colour, the zeros still to fill dimmed. */
@Composable
private fun EntryDisplay(digits: String) {
    val colors = MaterialTheme.colorScheme
    val padded = digits.padStart(TimerKeypad.MAX_DIGITS, '0')
    val firstTyped = TimerKeypad.MAX_DIGITS - digits.length
    val units = listOf(stringResource(R.string.timer_hours), stringResource(R.string.timer_min), stringResource(R.string.timer_sec))
    val unitStyle = MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = colors.onSurfaceVariant)
    val spoken = formatDuration(TimerKeypad.durationMs(digits) - MS_ROUND_DOWN)
    val text = buildAnnotatedString {
        padded.forEachIndexed { index, digit ->
            withStyle(SpanStyle(color = if (index >= firstTyped) colors.onSurface else colors.outlineVariant)) { append(digit) }
            if (index % 2 == 1) {
                withStyle(unitStyle) { append(units[index / 2]) }
                if (index < padded.lastIndex) append("  ")
            }
        }
    }
    Text(text, style = timeStyle(ENTRY_SIZE), maxLines = 1, modifier = Modifier.clearAndSetSemantics { contentDescription = spoken })
}

@Composable
private fun Presets(onPick: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Timers.PRESETS_MINUTES.split(',').map { it.toLong() }.forEach { minutes ->
            Text(
                stringResource(R.string.timer_minutes, minutes.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                modifier = Modifier.clip(CircleShape)
                    .background(colors.primaryContainer)
                    .clickable { onPick(minutes) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", DELETE_KEY))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    if (key == DELETE_KEY) {
                        Key(Modifier.weight(1f), onClick = onDelete, description = stringResource(R.string.timer_backspace)) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Key(Modifier.weight(1f), onClick = { onKey(key) }, description = key) {
                            Text(key, style = timeStyle(KEY_SIZE), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, onClick: () -> Unit, description: String, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.keyTone)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Keys and fields one step off the card, in light and dark. */
private val ColorScheme.keyTone: Color
    get() = if (isDark) surfaceContainerHighest else surfaceContainer

@Composable
private fun TimerCard(timer: TimerState, now: Long) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val remaining = timer.remaining(now)
    // A finished timer is ringing: the card turns to the accent until it is stopped.
    val ground = if (timer.finished) colors.primary else colors.card
    val ink = if (timer.finished) colors.onPrimary else colors.onSurface
    val soft = if (timer.finished) colors.onPrimary else colors.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(ground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = timer.label.ifBlank { stringResource(R.string.timer_default_label) }
                Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = soft, maxLines = 1)
                Text(formatDuration(remaining), style = timeStyle(CARD_TIME_SIZE), color = ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                val total = formatDuration(timer.durationMs - MS_ROUND_DOWN)
                Text(stringResource(R.string.timer_of, total), style = MaterialTheme.typography.bodyMedium, color = soft)
            }
            val progress = if (timer.durationMs > 0) remaining.toFloat() / timer.durationMs else 0f
            Ring(progress, size = 92.dp, stroke = 8.dp, track = ink.copy(alpha = 0.12f), color = if (timer.finished) colors.onPrimary else colors.primary) {
                val (icon, text, action) = when {
                    timer.finished -> Triple(Icons.Outlined.Replay, R.string.timer_reset) { Timers.reset(context, timer.id) }
                    timer.running -> Triple(Icons.Filled.Pause, R.string.timer_pause) { Timers.pause(context, timer.id) }
                    else -> Triple(Icons.Filled.PlayArrow, R.string.timer_resume) { Timers.resume(context, timer.id) }
                }
                RoundButton(icon, stringResource(text), size = 60.dp, inverted = timer.finished, onClick = action)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Timers.plusMinute(context, timer.id) }) { Text(stringResource(R.string.timer_plus_minute)) }
            if (!timer.finished && remaining < timer.durationMs) {
                FilledTonalButton(onClick = { Timers.reset(context, timer.id) }) { Text(stringResource(R.string.timer_reset)) }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { Timers.delete(context, timer.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.timer_delete), tint = soft)
            }
        }
    }
}

/** A circular track with an arc of [progress] (0..1) from twelve o'clock, around [content]. */
@Composable
private fun Ring(progress: Float, size: Dp, stroke: Dp, track: Color, color: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(size).drawBehind {
            val width = stroke.toPx()
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(track, 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(width))
            drawArc(
                color,
                START_ANGLE,
                FULL_TURN * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width, cap = StrokeCap.Round)
            )
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, size: Dp, inverted: Boolean = false, tonal: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val modifier = Modifier.size(size)
    if (tonal) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) { Icon(icon, contentDescription = description) }
    } else {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            colors = if (inverted) {
                IconButtonDefaults.filledIconButtonColors(containerColor = colors.onPrimary, contentColor = colors.primary)
            } else {
                IconButtonDefaults.filledIconButtonColors()
            }
        ) { Icon(icon, contentDescription = description, modifier = Modifier.size(size * ICON_RATIO)) }
    }
}

/**
 * RF-E3 in the "Amanecer" style: the time inside a ring that goes round once a minute, a large start/pause button with
 * lap or reset beside it, and the laps as cards. Keeps running with the app closed (state persisted, notification).
 */
@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val state by ClockStore.get(context).stopwatch.collectAsStateWithLifecycle()
    val now = rememberNow(if (state.running) STOPWATCH_TICK_MS else IDLE_TICK_MS)
    val elapsed = state.elapsed(now)
    val (main, hundredths) = formatElapsed(elapsed).split('.')
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(
            progress = (elapsed % MINUTE_MS) / MINUTE_MS.toFloat(),
            size = 264.dp,
            stroke = 10.dp,
            track = colors.onSurface.copy(alpha = 0.08f),
            color = colors.primary
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(main, style = timeStyle(if (main.length > MINUTES_TEXT) LONG_STOPWATCH_SIZE else STOPWATCH_SIZE), color = colors.onSurface)
                Text(".$hundredths", style = timeStyle(HUNDREDTHS_SIZE), color = colors.primary)
            }
        }
        Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.running -> RoundButton(Icons.Outlined.Flag, stringResource(R.string.stopwatch_lap), SIDE_BUTTON, tonal = true) { Stopwatch.lap(context) }
                state.accumulatedMs > 0 -> RoundButton(Icons.Outlined.Replay, stringResource(R.string.timer_reset), SIDE_BUTTON, tonal = true) {
                    Stopwatch.reset(context)
                }
                else -> Spacer(Modifier.size(SIDE_BUTTON))
            }
            RoundButton(
                if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (state.running) R.string.timer_pause else R.string.stopwatch_start),
                size = 88.dp
            ) { Stopwatch.toggle(context) }
            Spacer(Modifier.size(SIDE_BUTTON))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.lapDurations().withIndex().reversed().forEach { (index, lap) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.stopwatch_lap_n, index + 1),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(formatElapsed(lap), style = timeStyle(LAP_SIZE), color = colors.onSurface)
                    Text(
                        formatElapsed(state.laps[index]),
                        Modifier.width(96.dp).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
private val SIDE_BUTTON = 64.dp
private const val TIMER_TICK_MS = 250L
private const val STOPWATCH_TICK_MS = 30L
private const val IDLE_TICK_MS = 1_000L
private const val DELETE_KEY = "⌫"
private const val ENTRY_SIZE = 44
private const val KEY_SIZE = 26
private const val CARD_TIME_SIZE = 52
private const val STOPWATCH_SIZE = 64
private const val LONG_STOPWATCH_SIZE = 48
private const val HUNDREDTHS_SIZE = 28
private const val LAP_SIZE = 22
private const val MINUTES_TEXT = 5
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val ICON_RATIO = 0.45f

/** [formatDuration] rounds up for countdowns; a total that is shown as it is takes this off first. */
private const val MS_ROUND_DOWN = 999L
