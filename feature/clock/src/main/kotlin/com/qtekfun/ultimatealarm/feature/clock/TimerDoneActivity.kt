package com.qtekfun.ultimatealarm.feature.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qtekfun.ultimatealarm.core.designsystem.UltimateAlarmTheme
import kotlinx.coroutines.launch

/** Full screen when a timer ends (RF-E2): over the lock screen, Stop and +1 min. */
class TimerDoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerService.Ringing.current.collect { if (it.isEmpty()) finish() }
            }
        }
        setContent {
            UltimateAlarmTheme(darkTheme = true) {
                val ringing by TimerService.Ringing.current.collectAsStateWithLifecycle()
                val timers by ClockStore.get(this).timers.collectAsStateWithLifecycle()
                val id = ringing.firstOrNull() ?: return@UltimateAlarmTheme
                val label = timers.firstOrNull { it.id == id }?.label?.ifBlank { null } ?: stringResource(R.string.timer_default_label)
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
                ) {
                    Text(stringResource(R.string.timer_done, label), fontSize = 40.sp, color = MaterialTheme.colorScheme.onBackground)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(onClick = {
                            TimerService.send(this@TimerDoneActivity, TimerService.ACTION_PLUS_MINUTE, id)
                        }, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) {
                            Text(stringResource(R.string.timer_plus_minute), fontSize = 20.sp)
                        }
                        Button(onClick = {
                            TimerService.send(this@TimerDoneActivity, TimerService.ACTION_STOP, id)
                        }, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) {
                            Text(stringResource(R.string.timer_stop), fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
