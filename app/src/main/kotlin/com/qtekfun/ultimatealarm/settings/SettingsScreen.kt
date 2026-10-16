package com.qtekfun.ultimatealarm.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatealarm.R
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.data.VolumeButtonAction
import com.qtekfun.ultimatealarm.feature.sounds.SoundCatalog
import com.qtekfun.ultimatealarm.feature.sounds.SoundPickerDialog
import com.qtekfun.ultimatealarm.feature.sounds.SoundRef
import com.qtekfun.ultimatealarm.feature.sounds.soundName

/** Global ringing settings: volume buttons (RF-R7) and auto-silence (RF-R8). More sections arrive with their phases. */
@Composable
fun SettingsScreen(onHolidays: () -> Unit, modifier: Modifier = Modifier) {
    val store = RingStore(LocalContext.current)
    var volume by remember { mutableStateOf(store.volumeButtons) }
    var silence by remember { mutableStateOf(store.silenceAfterMinutes) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onHolidays)) {
            Text(stringResource(R.string.settings_holidays), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_holidays_summary),
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
