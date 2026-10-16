package com.qtekfun.ultimatealarm.feature.ringing

import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.RingMode
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.data.VolumeButtonAction
import com.qtekfun.ultimatealarm.core.designsystem.UltimateAlarmTheme
import com.qtekfun.ultimatealarm.feature.ringing.challenge.ChallengeContent
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The alarm screen over the lock screen, turning the screen on (RF-R1): huge time, label, Snooze and a swipe to dismiss.
 * Dark, legible at a distance (§10). The volume buttons do what the user chose (RF-R7).
 */
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
                val current by RingingState.current.collectAsStateWithLifecycle()
                val state = current ?: return@UltimateAlarmTheme
                var solving by rememberSaveable { mutableStateOf(false) }
                val challenge = state.challenge
                if (solving && challenge != null) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ChallengeContent(challenge, onSolved = { send(RingingService.ACTION_DISMISS) }, onCancel = { solving = false })
                    }
                } else {
                    RingingScreen(
                        title = state.title,
                        backup = state.mode == RingMode.BACKUP,
                        canSnooze = state.canSnooze,
                        onSnooze = { send(RingingService.ACTION_SNOOZE) },
                        onDismiss = { if (challenge == null) send(RingingService.ACTION_DISMISS) else solving = true }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(keyCode, event)
        when (RingStore(this).volumeButtons) {
            VolumeButtonAction.SNOOZE -> if (RingingState.current.value?.canSnooze == true) send(RingingService.ACTION_SNOOZE)
            // With a challenge, the volume buttons cannot dismiss (RF-R5): the swipe opens the challenge.
            VolumeButtonAction.DISMISS -> if (RingingState.current.value?.challenge == null) send(RingingService.ACTION_DISMISS)
            VolumeButtonAction.NOTHING -> Unit
        }
        // Consumed either way: the volume keys must not lower the alarm.
        return true
    }

    private fun send(action: String) {
        val alarmId = RingingState.current.value?.alarmId ?: return
        startService(RingingService.command(this, action, alarmId))
        finish()
    }
}

@Composable
private fun RingingScreen(title: String, backup: Boolean, canSnooze: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    // Back must not silence the alarm by accident.
    BackHandler {}
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (backup) {
            Text(stringResource(R.string.backup_ringing), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        }
        Text(
            DateFormat.getTimeFormat(context).format(Date(now)),
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(72.dp))
        if (canSnooze) {
            FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.ringing_snooze), fontSize = 22.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
        SwipeToDismiss(onDismiss)
    }
}

/** A thumb dragged to the end dismisses; TalkBack users get a plain click action on the same element (RNF-4). */
@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.swipe_to_dismiss)
    val dismissLabel = stringResource(R.string.ringing_dismiss)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(TRACK_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .semantics {
                contentDescription = label
                onClick(dismissLabel) {
                    onDismiss()
                    true
                }
            }
    ) {
        val density = LocalDensity.current
        val max = with(density) { (maxWidth - TRACK_HEIGHT).toPx() }
        val offset = remember { Animatable(0f) }
        Text(label, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.size(TRACK_HEIGHT).padding(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) } },
                    onDragStopped = {
                        if (offset.value > max * DISMISS_FRACTION) onDismiss() else scope.launch { offset.animateTo(0f) }
                    }
                )
        )
    }
}

private val TRACK_HEIGHT = 72.dp
private const val DISMISS_FRACTION = 0.8f
private const val CLOCK_TICK_MS = 1_000L
