package com.qtekfun.ultimatealarm.feature.ringing.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatealarm.core.data.Challenge
import com.qtekfun.ultimatealarm.core.data.MathProblems
import com.qtekfun.ultimatealarm.feature.ringing.R

/** The challenge between the swipe and the dismissal (RF-R5). [onCancel] goes back to the alarm, which keeps ringing. */
@Composable
fun ChallengeContent(challenge: Challenge, onSolved: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (challenge) {
            is Challenge.Math -> MathChallenge(challenge, onSolved)
            is Challenge.Code -> {
                Text(
                    stringResource(
                        R.string.challenge_scan,
                        challenge.alias.ifBlank {
                            stringResource(R.string.challenge_registered_code)
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                var wrong by remember { mutableStateOf(false) }
                CodeScanner(onCode = { if (it == challenge.value) onSolved() else wrong = true })
                if (wrong) Text(stringResource(R.string.challenge_wrong_code), color = MaterialTheme.colorScheme.error)
            }
            is Challenge.Nfc -> {
                Text(
                    stringResource(
                        R.string.challenge_nfc,
                        challenge.alias.ifBlank {
                            stringResource(R.string.challenge_registered_tag)
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                var wrong by remember { mutableStateOf(false) }
                NfcTagListener { if (it == challenge.tagId) onSolved() else wrong = true }
                if (wrong) Text(stringResource(R.string.challenge_wrong_tag), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.challenge_back)) }
    }
}

@Composable
private fun MathChallenge(challenge: Challenge.Math, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathProblems.generate(challenge.difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val submit = {
        if (MathProblems.check(problem, input)) {
            solved++
            if (solved >= challenge.operations) onSolved()
            problem = MathProblems.generate(challenge.difficulty)
            wrong = false
        } else {
            wrong = true
        }
        input = ""
    }
    Text(stringResource(R.string.challenge_math_progress, solved + 1, challenge.operations), style = MaterialTheme.typography.titleMedium)
    Text(problem.text + " =", fontSize = 48.sp, color = MaterialTheme.colorScheme.onBackground)
    OutlinedTextField(
        value = input,
        onValueChange = { value -> input = value.filter { it.isDigit() || it == '-' }.take(MAX_DIGITS) },
        singleLine = true,
        isError = wrong,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        label = { Text(stringResource(R.string.challenge_answer)) }
    )
    Button(onClick = submit, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.challenge_check)) }
}

private const val MAX_DIGITS = 6
