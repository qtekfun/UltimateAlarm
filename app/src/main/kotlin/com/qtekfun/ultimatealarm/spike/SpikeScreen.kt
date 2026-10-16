package com.qtekfun.ultimatealarm.spike

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qtekfun.ultimatealarm.R
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.Event
import com.qtekfun.ultimatealarm.core.data.TestAlarmStore
import com.qtekfun.ultimatealarm.feature.onboarding.Capability
import com.qtekfun.ultimatealarm.feature.onboarding.CapabilityList
import com.qtekfun.ultimatealarm.feature.onboarding.CapabilityProbe
import com.qtekfun.ultimatealarm.feature.onboarding.CapabilitySettings
import com.qtekfun.ultimatealarm.feature.ringing.TestAlarms
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 1 spike: one daily test alarm, the one-minute autotest, the capability diagnostics and the event log export.
 * Replaced by the alarm list in Phase 3.
 */
@Composable
fun SpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TestAlarmStore(context) }
    // Bumped on resume and after every action so the screen re-reads the store, the probe and the log.
    var refresh by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf(emptyList<Event>()) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val capabilities = remember(refresh) { CapabilityProbe.probe(context) }
    LaunchedEffect(refresh) {
        events = withContext(Dispatchers.IO) { DiagnosticLog.get(context).read() }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = DiagnosticLog.get(context).export(ZoneId.systemDefault())
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.spike_intro), style = MaterialTheme.typography.bodyMedium)

        Section(stringResource(R.string.spike_test_alarm)) {
            val time = remember(refresh) { store.time }
            val enabled = remember(refresh) { store.enabled }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(context, time), style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    TestAlarms.setTestAlarm(context, time, it)
                    refresh++
                })
            }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    TestAlarms.setTestAlarm(context, LocalTime.of(hour, minute), enabled = true)
                    refresh++
                }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            }) { Text(stringResource(R.string.spike_change_time)) }
            val scheduledAt = remember(refresh) { store.scheduledAt }
            Text(
                if (enabled && scheduledAt > 0) {
                    stringResource(R.string.spike_next, formatDateTime(scheduledAt))
                } else {
                    stringResource(R.string.spike_off)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Section(stringResource(R.string.spike_autotest)) {
            Text(stringResource(R.string.spike_autotest_help), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                TestAlarms.startAutotest(context)
                refresh++
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spike_autotest_start)) }
            Text(autotestStatus(events), style = MaterialTheme.typography.bodyMedium)
        }

        Section(stringResource(R.string.spike_diagnostics)) {
            CapabilityList(capabilities, onFix = { capability ->
                val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (capability == Capability.NOTIFICATIONS && needsRuntime) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    CapabilitySettings.open(context, capability)
                }
            })
            val forceMedia = remember(refresh) { store.forceMediaPlayback }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.spike_force_media), Modifier.weight(1f))
                Switch(checked = forceMedia, onCheckedChange = {
                    store.forceMediaPlayback = it
                    DiagnosticLog.log(context, "SETTING", "force_media_playback=$it")
                    refresh++
                })
            }
        }

        Section(stringResource(R.string.spike_event_log)) {
            Button(onClick = { export.launch("ultimatealarm-eventlog-${LocalDateTime.now().format(FILE_STAMP)}.txt") }) {
                Text(stringResource(R.string.spike_export))
            }
            events.takeLast(RECENT_EVENTS).asReversed().forEach { event ->
                Text(
                    "${formatDateTime(event.at.toEpochMilli())}  ${event.type}  ${event.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun autotestStatus(events: List<Event>): String {
    val last = events.lastOrNull { it.type == "AUTOTEST_START" || it.type == "AUTOTEST_RESULT" }
        ?: return stringResource(R.string.spike_autotest_none)
    if (last.type == "AUTOTEST_RESULT") return stringResource(R.string.spike_autotest_fired, last.detail.substringAfter("delay="))
    val due = last.detail.substringAfter("at=").let { runCatching { Instant.parse(it) }.getOrNull() }
    val missed = due != null && Instant.now().isAfter(due.plusSeconds(AUTOTEST_GRACE_SECONDS))
    return stringResource(if (missed) R.string.spike_autotest_missed else R.string.spike_autotest_pending)
}

private fun formatTime(context: Context, time: LocalTime): String {
    val millis = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateFormat.getTimeFormat(context).format(Date(millis))
}

private fun formatDateTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
private const val RECENT_EVENTS = 30
private const val AUTOTEST_GRACE_SECONDS = 120L
