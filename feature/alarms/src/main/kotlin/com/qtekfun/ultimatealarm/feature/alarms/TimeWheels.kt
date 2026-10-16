package com.qtekfun.ultimatealarm.feature.alarms

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatealarm.core.designsystem.timeStyle
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * D-19: the time as two scroll wheels (hours, minutes; plus AM/PM in 12-hour format) instead of the clock dial. The
 * keyboard button types the time instead, which is also the accessible way to set it.
 */
@Composable
fun TimeWheels(time: LocalTime, onTime: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    var typing by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val hourLabel = stringResource(R.string.wheel_hour)
        val minuteLabel = stringResource(R.string.wheel_minute)
        if (is24h) {
            Wheel(HOURS, time.hour, loop = true, description = hourLabel, label = ::twoDigits) { onTime(time.withHour(it)) }
        } else {
            val pm = time.hour >= NOON
            Wheel(HALF_DAY, (time.hour % HALF_DAY + HALF_DAY - 1) % HALF_DAY, loop = true, description = hourLabel, label = { (it + 1).toString() }) {
                onTime(time.withHour((it + 1) % HALF_DAY + if (pm) NOON else 0))
            }
        }
        Text(
            ":",
            style = timeStyle(WHEEL_SIZE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Wheel(MINUTES, time.minute, loop = true, description = minuteLabel, label = ::twoDigits) { onTime(time.withMinute(it)) }
        if (!is24h) {
            val am = stringResource(R.string.wheel_am)
            val pm = stringResource(R.string.wheel_pm)
            Wheel(2, if (time.hour >= NOON) 1 else 0, loop = false, description = "$am / $pm", label = { if (it == 0) am else pm }, small = true) {
                onTime(time.withHour(time.hour % NOON + if (it == 1) NOON else 0))
            }
        }
        IconButton(onClick = { typing = true }) {
            Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.wheel_type_time), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (typing) TypeTimeDialog(time, is24h, onTime = onTime, onDismiss = { typing = false })
}

@Composable
private fun Wheel(count: Int, selected: Int, loop: Boolean, description: String, label: (Int) -> String, small: Boolean = false, onSelected: (Int) -> Unit) {
    val total = if (loop) count * LOOPS else count
    val base = if (loop) count * (LOOPS / 2) else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected)
    val centered by remember(state) { derivedStateOf { centeredIndex(state) } }
    // The collector below outlives recompositions: read the latest value and callback, not the first ones.
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelected)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centered }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, index) -> index % count }
            .distinctUntilChanged()
            .collect { if (it != current) select(it) }
    }
    // A change from outside (typed time, AM/PM): move the wheel to it.
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && centered % count != selected) state.scrollToItem(centered - centered % count + selected)
    }
    val colors = MaterialTheme.colorScheme
    val width = if (small) 72.dp else 108.dp
    Box(
        Modifier.width(width).height(ITEM * VISIBLE).semantics {
            contentDescription = "$description ${label(selected)}"
        },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().height(ITEM).clip(RoundedCornerShape(26.dp)).background(colors.primaryContainer))
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = ITEM),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(total) { index ->
                val value = index % count
                val isCentered = index == centered
                Box(Modifier.height(ITEM).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label(value),
                        style = if (small) MaterialTheme.typography.titleLarge else timeStyle(if (isCentered) WHEEL_SIZE else SIDE_SIZE),
                        color = if (isCentered) colors.onPrimaryContainer else colors.outline
                    )
                }
            }
        }
    }
}

private fun centeredIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: state.firstVisibleItemIndex
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeTimeDialog(time: LocalTime, is24h: Boolean, onTime: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(time.hour, time.minute, is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimeInput(state = picker) },
        confirmButton = {
            TextButton(onClick = {
                onTime(LocalTime.of(picker.hour, picker.minute))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun twoDigits(value: Int) = String.format(Locale.ROOT, "%02d", value)

private val ITEM = 76.dp
private const val VISIBLE = 3
private const val LOOPS = 200
private const val HOURS = 24
private const val MINUTES = 60
private const val HALF_DAY = 12
private const val NOON = 12
private const val WHEEL_SIZE = 64
private const val SIDE_SIZE = 30
