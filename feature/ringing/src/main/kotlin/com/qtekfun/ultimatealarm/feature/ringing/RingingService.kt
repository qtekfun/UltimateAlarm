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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.DiagnosticsStore
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import kotlinx.coroutines.launch

/**
 * Foreground service that rings (02-diseno §5): a full-screen notification that opens [RingingActivity] over the lock
 * screen, the alarm sound and the vibration. The spike picks the foreground service type at runtime and logs it:
 * `systemExempted` when the app holds the exact alarm permission (Android 14+), `mediaPlayback` otherwise or on request.
 */
class RingingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: AlarmPlayer? = null
    private var vibrator: AlarmVibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.AUTOTEST_ID) ?: AlarmScheduler.AUTOTEST_ID
        when (intent?.action) {
            ACTION_DISMISS -> {
                DiagnosticLog.log(this, "DISMISSED", "", alarmId)
                stopRinging()
                ringingScope.launch { afterDismiss(applicationContext, alarmId) }
            }
            ACTION_SNOOZE -> {
                stopRinging()
                ringingScope.launch { snooze(applicationContext, alarmId) }
            }
            else -> ring(alarmId, intent?.getLongExtra(EXTRA_FIRED_AT, System.currentTimeMillis()) ?: System.currentTimeMillis())
        }
        return START_NOT_STICKY
    }

    private fun ring(alarmId: Long, firedAt: Long) {
        // Foreground and sound first, with a generic title: nothing that can fail or wait comes before the alarm sounds.
        goForeground(alarmId, getString(R.string.alarm_default_label))
        acquireWakeLock()
        player?.stop()
        vibrator?.stop()
        player = AlarmPlayer(this) { source -> DiagnosticLog.log(this, "SOUND", source, alarmId) }.also { it.start() }
        RingingState.set(Ringing(alarmId, firedAt))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            DiagnosticLog.log(this, "TIMEOUT", "silenced after ${MAX_RING_MS / MINUTE_MS} min", alarmId)
            stopRinging()
        }, MAX_RING_MS)
        ringingScope.launch {
            val alarm = if (alarmId ==
                AlarmScheduler.AUTOTEST_ID
            ) {
                null
            } else {
                runCatching { AlarmGraph.repository(applicationContext).alarm(alarmId) }.getOrNull()
            }
            val title = if (alarmId ==
                AlarmScheduler.AUTOTEST_ID
            ) {
                getString(R.string.ringing_title_autotest)
            } else {
                alarm?.let { Notifications.label(this@RingingService, it) }
            }
            val vibrate = alarm?.vibrate ?: true
            handler.post {
                if (RingingState.current.value?.alarmId != alarmId) return@post
                RingingState.set(Ringing(alarmId, firedAt, title.orEmpty()))
                title?.let { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(alarmId, it)) }
                if (vibrate) vibrator = AlarmVibrator(this@RingingService).also { runCatching { it.start() } }
            }
        }
    }

    private fun goForeground(alarmId: Long, title: String) {
        val notification = notification(alarmId, title)
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

    private fun notification(alarmId: Long, title: String): Notification {
        ensureChannel(this)
        val screen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.ringing_snooze), actionIntent(this, ACTION_SNOOZE, alarmId))
            .addAction(0, getString(R.string.ringing_dismiss), actionIntent(this, ACTION_DISMISS, alarmId))
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UltimateAlarm:ringing")
            .apply { acquire(MAX_RING_MS + MINUTE_MS) }
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        player = null
        vibrator?.stop()
        vibrator = null
        RingingState.set(null)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (player != null) stopRinging()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_RING = "com.qtekfun.ultimatealarm.action.RING"
        internal const val ACTION_DISMISS = "com.qtekfun.ultimatealarm.action.DISMISS"
        internal const val ACTION_SNOOZE = "com.qtekfun.ultimatealarm.action.SNOOZE"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_FIRED_AT = "fired_at"
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val MINUTE_MS = 60_000L
        private const val DEFAULT_SNOOZE_MINUTES = 10
        private const val MAX_RING_MS = 30 * MINUTE_MS

        /** One-shot alarms are deleted on dismiss, with a 10 s undo (RF-P5, §5.6); recurring ones are already re-scheduled. */
        internal suspend fun afterDismiss(context: Context, alarmId: Long) {
            if (alarmId == AlarmScheduler.AUTOTEST_ID) return
            AlarmScheduler.cancel(context, alarmId, snooze = true)
            val repository = AlarmGraph.repository(context)
            val alarm = repository.alarm(alarmId) ?: return
            if (alarm.kind == AlarmKind.ONE_SHOT) {
                UndoDeleteReceiver.recentlyDeleted[alarmId] = alarm
                repository.delete(alarmId)
                Notifications.deleted(context, alarm)
                DiagnosticLog.log(context, "DELETED", "one-shot dismissed", alarmId)
            }
        }

        internal suspend fun snooze(context: Context, alarmId: Long) {
            val minutes = if (alarmId ==
                AlarmScheduler.AUTOTEST_ID
            ) {
                DEFAULT_SNOOZE_MINUTES
            } else {
                AlarmGraph.repository(context).alarm(alarmId)?.snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES
            }
            AlarmScheduler.schedule(context, alarmId, System.currentTimeMillis() + minutes * MINUTE_MS, snooze = true)
            DiagnosticLog.log(context, "SNOOZED", "minutes=$minutes", alarmId)
        }

        fun startIntent(context: Context, alarmId: Long, firedAt: Long): Intent = Intent(context, RingingService::class.java)
            .setAction(ACTION_RING)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_FIRED_AT, firedAt)

        internal fun command(context: Context, action: String, alarmId: Long): Intent = Intent(context, RingingService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ALARM_ID, alarmId)

        private fun actionIntent(context: Context, action: String, alarmId: Long): PendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
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
