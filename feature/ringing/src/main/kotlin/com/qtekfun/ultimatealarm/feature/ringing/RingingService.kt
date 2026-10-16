package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.DiagnosticsStore
import com.qtekfun.ultimatealarm.core.data.RingMode
import com.qtekfun.ultimatealarm.core.data.RingPolicy
import com.qtekfun.ultimatealarm.core.data.RingState
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import kotlinx.coroutines.launch

/**
 * Foreground service that rings (02-diseno §5): full-screen notification opening [RingingActivity] over the lock screen,
 * the alarm sound with its ramp, vibration, the backup tone (RF-R4) with a safety alarm, snooze limits (RF-R3) and
 * auto-silence (RF-R8). The ring is persisted in [RingStore] so it resumes if the process dies (§5.4).
 *
 * Order matters: foreground and sound start before anything that reads the database, so nothing slow or failing can
 * delay the alarm; the alarm's own settings are applied a moment later.
 */
class RingingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: AlarmPlayer? = null
    private var vibrator: AlarmVibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: Session? = null
    private var savedAlarmVolume: Int? = null

    /** The ring in progress. [alarm] is `null` until loaded (or for the autotest). */
    private data class Session(val state: RingState, val alarm: Alarm?, val mode: RingMode)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.AUTOTEST_ID) ?: AlarmScheduler.AUTOTEST_ID
        when (intent?.action) {
            ACTION_DISMISS -> dismiss(alarmId)
            ACTION_SNOOZE -> snooze(alarmId)
            ACTION_DISMISS_SNOOZED -> {
                stopIfIdle()
                ringingScope.launch { endSnoozed(applicationContext, alarmId) }
            }
            ACTION_RING -> {
                val purpose = AlarmPurpose.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PURPOSE) } ?: AlarmPurpose.OCCURRENCE
                ring(alarmId, intent.getLongExtra(EXTRA_FIRED_AT, System.currentTimeMillis()), purpose)
            }
            // Restarted by the system after the process died (START_STICKY): resume the persisted ring, if any.
            else -> resume()
        }
        return START_STICKY
    }

    private fun resume() {
        val state = RingStore(this).state
        if (state == null || !RingPolicy.isResumable(state, System.currentTimeMillis())) {
            RingStore(this).state = null
            stopIfIdle()
            return
        }
        DiagnosticLog.log(this, "RESUMED", "after process death backup=${state.backupFired}", state.alarmId)
        ring(state.alarmId, state.firedAt, if (state.backupFired) AlarmPurpose.BACKUP else AlarmPurpose.RESUME)
    }

    private fun ring(alarmId: Long, firedAt: Long, purpose: AlarmPurpose) {
        val current = session
        if (current != null && current.state.alarmId == alarmId && purpose == AlarmPurpose.BACKUP) {
            enterBackup("safety alarm")
            return
        }
        if (current != null && current.state.alarmId != alarmId) {
            // A second alarm while one rings: the newer one takes over; the older one counts as dismissed.
            DiagnosticLog.log(this, "REPLACED", "by alarm $alarmId", current.state.alarmId)
            finishSession()
        }
        goForeground(alarmId, getString(R.string.alarm_default_label))
        acquireWakeLock()
        maximizeAlarmStream()

        val store = RingStore(this)
        val snoozes = when (purpose) {
            AlarmPurpose.SNOOZE -> store.snoozesOf(alarmId).also {
                store.setSnoozedUntil(alarmId, null)
                SnoozeNotifications.cancel(this, alarmId)
            }
            AlarmPurpose.RESUME, AlarmPurpose.BACKUP -> store.state?.takeIf { it.alarmId == alarmId }?.snoozeCount ?: 0
            AlarmPurpose.OCCURRENCE -> 0.also { store.setSnoozes(alarmId, null) }
        }
        val startMode = if (purpose == AlarmPurpose.BACKUP) RingMode.BACKUP else RingMode.NORMAL
        val state = RingState(alarmId, firedAt, snoozes, backupFired = startMode == RingMode.BACKUP)
        store.state = state
        session = Session(state, null, startMode)

        player?.stop()
        player = AlarmPlayer(this, initialGain(startMode)) { source -> DiagnosticLog.log(this, "SOUND", source, alarmId) }.also { it.start() }
        RingingState.set(Ringing(alarmId, firedAt, mode = startMode))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(
            { silence() },
            RingPolicy.silenceAt(firedAt, Alarm(backupAfterMinutes = null), store.silenceAfterMinutes) - System.currentTimeMillis()
        )
        ringingScope.launch { applyAlarm(alarmId) }
    }

    private fun initialGain(mode: RingMode) = if (mode == RingMode.BACKUP) 1f else RingPolicy.gain(0, Alarm().rampSeconds, Alarm().maxVolume)

    /** Second step of a ring: the alarm's own volume, ramp, vibration, title, snooze state, backup and silence timers. */
    private suspend fun applyAlarm(alarmId: Long) {
        val alarm = if (alarmId == AlarmScheduler.AUTOTEST_ID) null else runCatching { AlarmGraph.repository(applicationContext).alarm(alarmId) }.getOrNull()
        handler.post {
            val current = session?.takeIf { it.state.alarmId == alarmId } ?: return@post
            val settings = alarm ?: Alarm(backupAfterMinutes = null)
            val mode = if (current.mode == RingMode.BACKUP) RingMode.BACKUP else RingPolicy.modeForRing(current.state.snoozeCount, settings)
            session = current.copy(alarm = alarm, mode = mode)
            val title = if (alarm == null) getString(R.string.ringing_title_autotest) else Notifications.label(this, alarm)
            val canSnooze = RingPolicy.canSnooze(current.state.snoozeCount, settings.maxSnoozes)
            RingingState.set(Ringing(alarmId, current.state.firedAt, title, canSnooze, mode))
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(alarmId, title, canSnooze))

            handler.removeCallbacksAndMessages(null)
            if (mode == RingMode.BACKUP) {
                enterBackup(if (current.mode == RingMode.BACKUP) "resumed" else "snoozes used up")
            } else {
                if (settings.vibrate) vibrator = AlarmVibrator(this).also { runCatching { it.start() } }
                startRamp(current.state.firedAt, settings)
                RingPolicy.backupAt(current.state.firedAt, settings)?.let { at ->
                    handler.postDelayed({ enterBackup("no interaction") }, at - System.currentTimeMillis())
                    AlarmScheduler.schedule(this, alarmId, at, AlarmPurpose.BACKUP)
                }
            }
            val silenceAt = RingPolicy.silenceAt(current.state.firedAt, settings, RingStore(this).silenceAfterMinutes)
            handler.postDelayed({ silence() }, silenceAt - System.currentTimeMillis())
        }
    }

    private fun startRamp(firedAt: Long, alarm: Alarm) {
        val tick = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - firedAt
                player?.setGain(RingPolicy.gain(elapsed, alarm.rampSeconds, alarm.maxVolume))
                if (elapsed < alarm.rampSeconds * MS_PER_SECOND) handler.postDelayed(this, RAMP_TICK_MS)
            }
        }
        tick.run()
    }

    /** RF-R4: backup tone at full volume, no ramp, continuous vibration. */
    private fun enterBackup(reason: String) {
        val current = session ?: return
        val alarmId = current.state.alarmId
        session = current.copy(mode = RingMode.BACKUP, state = current.state.copy(backupFired = true))
        RingStore(this).state = session?.state
        DiagnosticLog.log(this, "BACKUP", reason, alarmId)
        // The bundled backup tone arrives in Phase 5; meanwhile the same chain plays at full volume.
        player?.setGain(1f)
        vibrator?.stop()
        vibrator = AlarmVibrator(this).also { runCatching { it.start(continuous = true) } }
        RingingState.current.value?.let { RingingState.set(it.copy(mode = RingMode.BACKUP)) }
        AlarmScheduler.cancel(this, alarmId, AlarmPurpose.BACKUP)
    }

    private fun dismiss(alarmId: Long) {
        DiagnosticLog.log(this, "DISMISSED", "", alarmId)
        finishSession()
        RingStore(this).setSnoozes(alarmId, null)
        stopIfIdle()
        ringingScope.launch { afterDismiss(applicationContext, alarmId) }
    }

    private fun snooze(alarmId: Long) {
        val current = session?.takeIf { it.state.alarmId == alarmId }
        val alarm = current?.alarm
        val count = current?.state?.snoozeCount ?: 0
        if (alarm != null && !RingPolicy.canSnooze(count, alarm.maxSnoozes)) {
            DiagnosticLog.log(this, "SNOOZE_REFUSED", "limit ${alarm.maxSnoozes} reached", alarmId)
            return
        }
        val minutes = alarm?.snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES
        val until = System.currentTimeMillis() + minutes * MS_PER_MINUTE
        RingStore(this).setSnoozes(alarmId, count + 1)
        RingStore(this).setSnoozedUntil(alarmId, until)
        AlarmScheduler.schedule(this, alarmId, until, AlarmPurpose.SNOOZE)
        DiagnosticLog.log(this, "SNOOZED", "minutes=$minutes count=${count + 1}", alarmId)
        val label = alarm?.let { Notifications.label(this, it) } ?: getString(R.string.ringing_title_autotest)
        finishSession()
        SnoozeNotifications.show(this, alarmId, label, until)
        stopIfIdle()
    }

    /** RF-R8. The alarm is not dismissed: a one-shot alarm stays, flagged by its last firing. */
    private fun silence() {
        val alarmId = session?.state?.alarmId ?: return
        DiagnosticLog.log(this, "TIMEOUT", "silenced without interaction", alarmId)
        finishSession()
        RingStore(this).setSnoozes(alarmId, null)
        stopIfIdle()
    }

    /** Stops sound, vibration and timers of the current ring and forgets it. */
    private fun finishSession() {
        session?.let { AlarmScheduler.cancel(this, it.state.alarmId, AlarmPurpose.BACKUP) }
        session = null
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        player = null
        vibrator?.stop()
        vibrator = null
        RingStore(this).state = null
        RingingState.set(null)
        restoreAlarmStream()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopIfIdle() {
        if (session != null) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The software gain does the volume (max volume, ramp); the stream itself goes to its maximum while ringing. */
    private fun maximizeAlarmStream() {
        if (savedAlarmVolume != null) return
        val audio = getSystemService(AudioManager::class.java)
        runCatching {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }.onFailure { DiagnosticLog.log(this, "ERROR", "alarm stream volume: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun restoreAlarmStream() {
        val saved = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
    }

    private fun goForeground(alarmId: Long, title: String) {
        val notification = notification(alarmId, title, canSnooze = true)
        val preferred = preferredType()
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, preferred) }
            .onSuccess { DiagnosticLog.log(this, "FGS_STARTED", "type=${typeName(preferred)} full_screen=${canUseFullScreenIntent()}", alarmId) }
            .onFailure { e ->
                // A refused type must not stop the alarm: retry with mediaPlayback and keep ringing either way.
                DiagnosticLog.log(this, "ERROR", "startForeground ${typeName(preferred)}: ${e.javaClass.simpleName}: ${e.message}", alarmId)
                val fallback = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fallback) }
                    .onSuccess { DiagnosticLog.log(this, "FGS_STARTED", "type=${typeName(fallback)} (fallback)", alarmId) }
                    .onFailure { DiagnosticLog.log(this, "ERROR", "startForeground fallback: ${it.javaClass.simpleName}: ${it.message}", alarmId) }
            }
    }

    private fun preferredType(): Int {
        val exact = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && exact && !DiagnosticsStore(this).forceMediaPlayback) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
    }

    private fun typeName(type: Int) = when (type) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED -> "systemExempted"
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK -> "mediaPlayback"
        else -> type.toString()
    }

    private fun canUseFullScreenIntent(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun notification(alarmId: Long, title: String, canSnooze: Boolean): Notification {
        ensureChannel(this)
        val screen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (canSnooze) builder.addAction(0, getString(R.string.ringing_snooze), actionIntent(this, ACTION_SNOOZE, alarmId))
        return builder.addAction(0, getString(R.string.ringing_dismiss), actionIntent(this, ACTION_DISMISS, alarmId)).build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UltimateAlarm:ringing")
            .apply { acquire(RingPolicy.HARD_LIMIT_MINUTES * MS_PER_MINUTE + MS_PER_MINUTE) }
    }

    override fun onDestroy() {
        // Destroyed by the system while ringing: the persisted RingState and the backup safety alarm bring it back.
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        vibrator?.stop()
        restoreAlarmStream()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_RING = "com.qtekfun.ultimatealarm.action.RING"
        internal const val ACTION_DISMISS = "com.qtekfun.ultimatealarm.action.DISMISS"
        internal const val ACTION_SNOOZE = "com.qtekfun.ultimatealarm.action.SNOOZE"
        internal const val ACTION_DISMISS_SNOOZED = "com.qtekfun.ultimatealarm.action.DISMISS_SNOOZED"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_FIRED_AT = "fired_at"
        private const val EXTRA_PURPOSE = "purpose"
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val MS_PER_SECOND = 1_000L
        private const val MS_PER_MINUTE = 60_000L
        private const val RAMP_TICK_MS = 250L
        private const val DEFAULT_SNOOZE_MINUTES = 10

        /** One-shot alarms are deleted on dismiss, with a 10 s undo (RF-P5, §5.6); recurring ones are already re-scheduled. */
        internal suspend fun afterDismiss(context: Context, alarmId: Long) {
            if (alarmId == AlarmScheduler.AUTOTEST_ID) return
            val repository = AlarmGraph.repository(context)
            val alarm = repository.alarm(alarmId) ?: return
            if (alarm.kind == AlarmKind.ONE_SHOT) {
                UndoDeleteReceiver.recentlyDeleted[alarmId] = alarm
                repository.delete(alarmId)
                Notifications.deleted(context, alarm)
                DiagnosticLog.log(context, "DELETED", "one-shot dismissed", alarmId)
            }
        }

        /** "Dismiss" on the "Snoozed until HH:MM" notification: the snooze is cancelled and the alarm counts as dismissed. */
        internal suspend fun endSnoozed(context: Context, alarmId: Long) {
            AlarmScheduler.cancel(context, alarmId, AlarmPurpose.SNOOZE)
            RingStore(context).setSnoozes(alarmId, null)
            RingStore(context).setSnoozedUntil(alarmId, null)
            SnoozeNotifications.cancel(context, alarmId)
            DiagnosticLog.log(context, "DISMISSED", "while snoozed", alarmId)
            afterDismiss(context, alarmId)
        }

        fun startIntent(context: Context, alarmId: Long, firedAt: Long, purpose: AlarmPurpose = AlarmPurpose.OCCURRENCE): Intent =
            Intent(context, RingingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_FIRED_AT, firedAt)
                .putExtra(EXTRA_PURPOSE, purpose.name)

        internal fun command(context: Context, action: String, alarmId: Long): Intent = Intent(context, RingingService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ALARM_ID, alarmId)

        internal fun actionIntent(context: Context, action: String, alarmId: Long): PendingIntent = PendingIntent.getService(
            context,
            (action.hashCode() * 31 + alarmId).toInt(),
            command(context, action, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** Silent channel: the service plays the sound itself on the alarm stream. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.ringing_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ringing_channel_description)
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
