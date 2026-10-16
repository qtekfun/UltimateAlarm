package com.qtekfun.ultimatealarm.feature.ringing

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.designsystem.UltimateAlarmTheme
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The alarm screen, over the lock screen and turning the screen on (RF-R1). The definitive design comes in Phase 4. */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val ringing = RingingState.current.value
        if (ringing == null) {
            finish()
            return
        }
        DiagnosticLog.log(this, "SCREEN_SHOWN", "after=${System.currentTimeMillis() - ringing.firedAt}ms", ringing.alarmId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RingingState.current.collect { if (it == null) finish() }
            }
        }
        setContent {
            UltimateAlarmTheme(darkTheme = true) {
                RingingScreen(
                    onSnooze = { send(RingingService.ACTION_SNOOZE, ringing.alarmId) },
                    onDismiss = { send(RingingService.ACTION_DISMISS, ringing.alarmId) }
                )
            }
        }
    }

    private fun send(action: String, alarmId: Long) {
        startService(RingingService.command(this, action, alarmId))
        finish()
    }
}

@Composable
private fun RingingScreen(onSnooze: () -> Unit, onDismiss: () -> Unit) {
    // Back must not silence the alarm by accident.
    BackHandler {}
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            DateFormat.getTimeFormat(context).format(Date(now)),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(64.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) {
                Text(stringResource(R.string.ringing_snooze_minutes, RingingService.SNOOZE_MINUTES), fontSize = 20.sp)
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) {
                Text(stringResource(R.string.ringing_dismiss), fontSize = 20.sp)
            }
        }
    }
}
