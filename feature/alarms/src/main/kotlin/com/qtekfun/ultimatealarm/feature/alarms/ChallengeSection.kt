package com.qtekfun.ultimatealarm.feature.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatealarm.core.data.Challenge
import com.qtekfun.ultimatealarm.feature.ringing.challenge.CodeScanner
import com.qtekfun.ultimatealarm.feature.ringing.challenge.NfcTagListener
import com.qtekfun.ultimatealarm.feature.ringing.challenge.hasCamera
import com.qtekfun.ultimatealarm.feature.ringing.challenge.hasNfc
import kotlin.math.roundToInt

private enum class ChallengeKind { NONE, MATH, CODE, NFC }

/**
 * RF-R5 in the editor: none, maths (difficulty 1–3, number of problems), a QR/barcode registered by scanning it, or an
 * NFC tag registered by holding it. Missing hardware is shown here: such a challenge would ring as maths.
 */
@Composable
fun ChallengeSection(encoded: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val challenge = Challenge.decode(encoded)
    val camera = remember { hasCamera(context) }
    val nfc = remember { hasNfc(context) }
    var registering by rememberSaveable { mutableStateOf<ChallengeKind?>(null) }
    val kind = when (challenge) {
        null -> ChallengeKind.NONE
        is Challenge.Math -> ChallengeKind.MATH
        is Challenge.Code -> ChallengeKind.CODE
        is Challenge.Nfc -> ChallengeKind.NFC
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.challenge_help), style = MaterialTheme.typography.bodySmall)
        Choice(stringResource(R.string.challenge_none), kind == ChallengeKind.NONE) { onChange(null) }
        Choice(stringResource(R.string.challenge_math), kind == ChallengeKind.MATH) { onChange(Challenge.Math().encode()) }
        if (challenge is Challenge.Math) {
            Text(stringResource(R.string.challenge_difficulty, challenge.difficulty))
            Slider(
                value = challenge.difficulty.toFloat(),
                onValueChange = { onChange(challenge.copy(difficulty = it.roundToInt()).encode()) },
                valueRange = 1f..Challenge.MAX_DIFFICULTY.toFloat(),
                steps = Challenge.MAX_DIFFICULTY - 2
            )
            Text(stringResource(R.string.challenge_operations, challenge.operations))
            Slider(
                value = challenge.operations.toFloat(),
                onValueChange = { onChange(challenge.copy(operations = it.roundToInt()).encode()) },
                valueRange = 1f..MAX_PROBLEMS.toFloat(),
                steps = MAX_PROBLEMS - 2
            )
        }
        Choice(stringResource(R.string.challenge_code), kind == ChallengeKind.CODE) { registering = ChallengeKind.CODE }
        if (!camera) Warning(stringResource(R.string.challenge_no_camera))
        if (challenge is Challenge.Code) Registered(challenge.alias.ifBlank { challenge.value.take(PREVIEW_CHARS) }) { registering = ChallengeKind.CODE }
        Choice(stringResource(R.string.challenge_nfc_tag), kind == ChallengeKind.NFC) { registering = ChallengeKind.NFC }
        if (!nfc) Warning(stringResource(R.string.challenge_no_nfc))
        if (challenge is Challenge.Nfc) Registered(challenge.alias.ifBlank { challenge.tagId }) { registering = ChallengeKind.NFC }
    }
    registering?.let { target ->
        RegisterDialog(
            target,
            onDone = { value, alias ->
                registering = null
                onChange(if (target == ChallengeKind.CODE) Challenge.Code(value, alias).encode() else Challenge.Nfc(value, alias).encode())
            },
            onDismiss = { registering = null }
        )
    }
}

@Composable
private fun RegisterDialog(kind: ChallengeKind, onDone: (String, String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf<String?>(null) }
    var alias by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (kind == ChallengeKind.CODE) R.string.challenge_register_code else R.string.challenge_register_nfc)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (value == null) {
                    if (kind == ChallengeKind.CODE) {
                        CodeScanner(onCode = { value = it })
                    } else {
                        Text(stringResource(R.string.challenge_hold_tag))
                        NfcTagListener { value = it }
                    }
                } else {
                    Text(stringResource(R.string.challenge_registered_ok))
                    OutlinedTextField(value = alias, onValueChange = {
                        alias = it
                    }, label = { Text(stringResource(R.string.challenge_alias)) }, singleLine = true)
                }
            }
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let { onDone(it, alias.trim()) } }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.weight(1f))
    }
}

@Composable
private fun Registered(name: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.challenge_registered, name), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onChange) { Text(stringResource(R.string.challenge_change)) }
    }
}

@Composable
private fun Warning(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

private const val MAX_PROBLEMS = 5
private const val PREVIEW_CHARS = 24
