package com.qtekfun.ultimatealarm.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatealarm.R
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.data.VolumeButtonAction
import com.qtekfun.ultimatealarm.core.designsystem.AccentColor
import com.qtekfun.ultimatealarm.core.designsystem.AppearanceStore
import com.qtekfun.ultimatealarm.core.designsystem.ThemeMode
import com.qtekfun.ultimatealarm.feature.ringing.Rescheduler
import com.qtekfun.ultimatealarm.feature.sounds.SoundCatalog
import com.qtekfun.ultimatealarm.feature.sounds.SoundPickerDialog
import com.qtekfun.ultimatealarm.feature.sounds.SoundRef
import com.qtekfun.ultimatealarm.feature.sounds.soundName
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Global ringing settings: volume buttons (RF-R7) and auto-silence (RF-R8). More sections arrive with their phases. */
@Composable
fun SettingsScreen(onHolidays: () -> Unit, onBackup: () -> Unit, onAssistant: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = RingStore(context)
    var volume by remember { mutableStateOf(store.volumeButtons) }
    var silence by remember { mutableStateOf(store.silenceAfterMinutes) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppearanceSection()
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onHolidays)) {
            Text(stringResource(R.string.settings_holidays), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_holidays_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onAssistant)) {
            Text(stringResource(R.string.settings_assistant), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_assistant_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onBackup)) {
            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_backup_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        var backupSound by remember { mutableStateOf(SoundRef.decode(store.backupSound)) }
        var pickingBackup by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { pickingBackup = true }) {
            Text(stringResource(com.qtekfun.ultimatealarm.feature.sounds.R.string.sound_backup_title), style = MaterialTheme.typography.titleMedium)
            Text(soundName(backupSound, SoundCatalog.DEFAULT_BACKUP), color = MaterialTheme.colorScheme.primary)
        }
        if (pickingBackup) {
            SoundPickerDialog(
                title = stringResource(com.qtekfun.ultimatealarm.feature.sounds.R.string.sound_backup_title),
                current = backupSound,
                backup = true,
                rampSeconds = 0,
                maxVolume = 100,
                noneLabel = soundName(null, SoundCatalog.DEFAULT_BACKUP),
                onPick = {
                    backupSound = it
                    store.backupSound = it?.encode()
                },
                onDismiss = { pickingBackup = false }
            )
        }
        var bedtime by remember { mutableStateOf(store.bedtimeEnabled) }
        var bedtimeHours by remember { mutableStateOf(store.bedtimeHours) }
        Group(stringResource(R.string.settings_bedtime)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_bedtime_enabled), Modifier.weight(1f))
                Switch(checked = bedtime, onCheckedChange = {
                    bedtime = it
                    store.bedtimeEnabled = it
                    scope.launch { Rescheduler.rescheduleAll(context, "settings") }
                })
            }
            if (bedtime) {
                Text(stringResource(R.string.settings_bedtime_hours, bedtimeHours))
                Slider(
                    value = bedtimeHours.toFloat(),
                    onValueChange = { bedtimeHours = it.roundToInt() },
                    onValueChangeFinished = {
                        store.bedtimeHours = bedtimeHours
                        scope.launch { Rescheduler.rescheduleAll(context, "settings") }
                    },
                    valueRange = 6f..10f,
                    steps = 3
                )
            }
        }
        Group(stringResource(R.string.settings_volume_buttons)) {
            listOf(
                VolumeButtonAction.SNOOZE to R.string.settings_volume_snooze,
                VolumeButtonAction.DISMISS to R.string.settings_volume_dismiss,
                VolumeButtonAction.NOTHING to R.string.settings_volume_nothing
            ).forEach { (action, label) ->
                Choice(label, selected = volume == action) {
                    volume = action
                    store.volumeButtons = action
                }
            }
        }
        Group(stringResource(R.string.settings_silence_after)) {
            SILENCE_CHOICES.forEach { minutes ->
                val label = if (minutes == null) R.string.settings_silence_default else R.string.settings_silence_minutes
                Choice(label, selected = silence == minutes, arg = minutes) {
                    silence = minutes
                    store.silenceAfterMinutes = minutes
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun Choice(@StringRes label: Int, selected: Boolean, arg: Int? = null, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(if (arg == null) stringResource(label) else stringResource(label, arg), Modifier.padding(start = 12.dp))
    }
}

private val SILENCE_CHOICES = listOf(null, 5, 10, 15, 30)

/** D-19: light, dark or the system's, and the accent of the "Amanecer" look (or the wallpaper's colours). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val appearance = AppearanceStore.current(context)
    Group(stringResource(R.string.settings_appearance)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(
                ThemeMode.SYSTEM to R.string.settings_theme_system,
                ThemeMode.LIGHT to R.string.settings_theme_light,
                ThemeMode.DARK to R.string.settings_theme_dark
            )
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = appearance.mode == mode,
                    onClick = { AppearanceStore.set(context, appearance.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            stringResource(R.string.settings_accent) + " · " + stringResource(accentName(appearance.accent)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentColor.entries.forEach { accent ->
                val selected = appearance.accent == accent
                val name = stringResource(accentName(accent))
                val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                Box(
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .border(3.dp, ring, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(swatch(accent))
                        .selectable(selected = selected, role = Role.RadioButton) { AppearanceStore.set(context, appearance.copy(accent = accent)) }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center
                ) {
                    if (accent == AccentColor.DYNAMIC) {
                        Icon(
                            Icons.Filled.Wallpaper,
                            contentDescription = null,
                            tint = dynamicLightColorScheme(context).onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The dynamic swatch shows the wallpaper's own primary colour. */
@Composable
private fun swatch(accent: AccentColor): Color = if (accent == AccentColor.DYNAMIC) {
    dynamicLightColorScheme(LocalContext.current).primary
} else {
    Color(OPAQUE or accent.rgb.toLong())
}

private const val OPAQUE = 0xFF000000

private fun accentName(accent: AccentColor) = when (accent) {
    AccentColor.ORANGE -> R.string.accent_orange
    AccentColor.RASPBERRY -> R.string.accent_raspberry
    AccentColor.PLUM -> R.string.accent_plum
    AccentColor.INDIGO -> R.string.accent_indigo
    AccentColor.TEAL -> R.string.accent_teal
    AccentColor.MUSTARD -> R.string.accent_mustard
    AccentColor.DYNAMIC -> R.string.accent_dynamic
}
