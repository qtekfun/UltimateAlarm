package com.qtekfun.ultimatealarm.feature.ringing

import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.qtekfun.ultimatealarm.core.designsystem.timeStyle
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

/**
 * D-19: a warm dark ground with the sun rising from the bottom (the accent), the time in the display face, a big
 * snooze button and the swipe to dismiss. Always dark, whatever the app's mode: it is seen in a dark room.
 */
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
    val sun = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(RING_GROUND)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height + SUN_DROP.toPx())
            drawCircle(sun.copy(alpha = HALO_FAR), radius = (SUN_RADIUS + HALO_STEP * 2).toPx(), center = center, style = Stroke(2.dp.toPx()))
            drawCircle(sun.copy(alpha = HALO_NEAR), radius = (SUN_RADIUS + HALO_STEP).toPx(), center = center, style = Stroke(2.dp.toPx()))
            drawCircle(sun, radius = SUN_RADIUS.toPx(), center = center)
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            if (backup) {
                Text(stringResource(R.string.backup_ringing), style = MaterialTheme.typography.titleLarge, color = RING_SOFT, textAlign = TextAlign.Center)
            }
            val locale = LocalConfiguration.current.locales[0]
            val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
            Text(
                DateFormat.format(pattern, now).toString().replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = RING_SOFT
            )
            Text(DateFormat.getTimeFormat(context).format(Date(now)), style = timeStyle(TIME_SIZE), color = RING_TEXT, modifier = Modifier.padding(top = 8.dp))
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = RING_TEXT,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (canSnooze) {
                Button(
                    onClick = onSnooze,
                    colors = ButtonDefaults.buttonColors(containerColor = RING_TEXT, contentColor = RING_GROUND),
                    modifier = Modifier.fillMaxWidth().heightIn(min = TRACK_HEIGHT)
                ) {
                    Icon(Icons.Outlined.Snooze, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ringing_snooze), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
            }
            SwipeToDismiss(onDismiss)
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** A thumb dragged to the end dismisses; TalkBack users get a plain click action on the same element (RNF-4). */
@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.swipe_to_dismiss)
    val dismissLabel = stringResource(R.string.ringing_dismiss)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(TRACK_HEIGHT)
            .background(RING_GROUND.copy(alpha = TRACK_ALPHA), CircleShape)
            .border(2.dp, RING_TEXT.copy(alpha = BORDER_ALPHA), CircleShape)
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
        Text(label, Modifier.align(Alignment.Center).padding(start = TRACK_HEIGHT), style = MaterialTheme.typography.titleMedium, color = RING_TEXT)
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.size(TRACK_HEIGHT).padding(6.dp)
                .background(RING_TEXT, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) } },
                    onDragStopped = {
                        if (offset.value > max * DISMISS_FRACTION) onDismiss() else scope.launch { offset.animateTo(0f) }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = RING_GROUND, modifier = Modifier.size(26.dp))
        }
    }
}

/** The ringing screen's colours are tinted by the chosen accent: a near-black ground, near-white text. */
private val RING_GROUND: Color @Composable get() = lerp(MaterialTheme.colorScheme.primary, Color.Black, GROUND_MIX)
private val RING_TEXT: Color @Composable get() = lerp(MaterialTheme.colorScheme.primary, Color.White, TEXT_MIX)
private val RING_SOFT: Color @Composable get() = lerp(MaterialTheme.colorScheme.primary, Color.White, SOFT_MIX)
private const val GROUND_MIX = 0.86f
private const val TEXT_MIX = 0.92f
private const val SOFT_MIX = 0.7f
private val SUN_RADIUS = 320.dp
private val SUN_DROP = 10.dp
private val HALO_STEP = 80.dp
private const val HALO_NEAR = 0.3f
private const val HALO_FAR = 0.15f
private const val TRACK_ALPHA = 0.55f
private const val BORDER_ALPHA = 0.35f
private const val TIME_SIZE = 112
private val TRACK_HEIGHT = 72.dp
private const val DISMISS_FRACTION = 0.8f
private const val CLOCK_TICK_MS = 1_000L
