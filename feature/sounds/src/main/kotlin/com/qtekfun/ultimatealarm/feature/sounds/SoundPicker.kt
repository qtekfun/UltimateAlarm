package com.qtekfun.ultimatealarm.feature.sounds

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.IntentCompat

/** What a sound row shows. */
@Composable
fun soundName(ref: SoundRef?, default: SoundRef = SoundCatalog.DEFAULT_ALARM): String = when (ref) {
    null -> stringResource(R.string.sound_default, bundledName(default))
    is SoundRef.Bundled -> SoundCatalog.byId(ref.id)?.let { stringResource(it.name) } ?: stringResource(R.string.sound_default, bundledName(default))
    is SoundRef.System -> stringResource(R.string.sound_system_chosen)
    is SoundRef.File -> stringResource(R.string.sound_file_chosen)
}

@Composable
private fun bundledName(ref: SoundRef): String = (ref as? SoundRef.Bundled)?.let { SoundCatalog.byId(it.id) }?.let { stringResource(it.name) }.orEmpty()

/**
 * Sound selector (RF-S1–S4): the pack by category, a system alarm tone or the user's own file (permission persisted).
 * Each pack entry can be previewed with the alarm's ramp and volume. [backup] lists backup tones; [allowNone] offers
 * "default" (the global backup tone, or the pack default).
 */
@Composable
fun SoundPickerDialog(
    title: String,
    current: SoundRef?,
    backup: Boolean,
    rampSeconds: Int,
    maxVolume: Int,
    noneLabel: String,
    onPick: (SoundRef?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preview = remember { PreviewPlayer(context) }
    var playing by remember { mutableStateOf<SoundRef?>(null) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        if (uri != null) onPick(SoundRef.System(uri.toString()))
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // RF-S3: keep access after reboots; if the file disappears the fallback chain plays the default.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPick(SoundRef.File(uri.toString()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Choice(noneLabel, current == null, onSelect = { onPick(null) })
                val choices = if (backup) SoundCatalog.backupChoices() else SoundCatalog.alarmChoices()
                choices.groupBy { it.category }.forEach { (category, sounds) ->
                    Text(stringResource(category.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    sounds.forEach { sound ->
                        val ref = SoundRef.Bundled(sound.id)
                        Choice(stringResource(sound.name), current == ref, onSelect = { onPick(ref) }) {
                            IconButton(onClick = {
                                if (playing == ref) {
                                    preview.stop()
                                    playing = null
                                } else {
                                    preview.play(ref, if (backup) 0 else rampSeconds, if (backup) MAX_VOLUME else maxVolume)
                                    playing = ref
                                }
                            }) {
                                Icon(
                                    if (playing == ref) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(if (playing == ref) R.string.sound_stop else R.string.sound_preview)
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    preview.stop()
                    system.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    )
                }) { Text(stringResource(R.string.sound_system)) }
                TextButton(onClick = {
                    preview.stop()
                    file.launch(arrayOf("audio/*"))
                }) { Text(stringResource(R.string.sound_file)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
        trailing()
    }
}

private const val MAX_VOLUME = 100
