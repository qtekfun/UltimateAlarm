package com.qtekfun.ultimatealarm.feature.clock

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.feature.sounds.SoundRef
import com.qtekfun.ultimatealarm.feature.sounds.TonePlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** RF-E2: a finished timer rings with the Notification category tone and opens a full screen with Stop and +1 min. */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: TonePlayer? = null
    private val ringing = mutableSetOf<Long>()

    /**
     * The newest start command. Stopping with it (instead of a bare stopSelf) never kills a service that has just been asked
     * to ring again: an alarm firing right after another was dismissed must keep ringing.
     */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getLongExtra(EXTRA_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_STOP -> stop(id, plusMinute = false)
            ACTION_PLUS_MINUTE -> stop(id, plusMinute = true)
            else -> ring(id)
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Long) {
        ringing += id
        val notification = notification(id)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .recoverCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
            .onFailure { DiagnosticLog.log(this, "ERROR", "timer foreground: ${it.javaClass.simpleName}: ${it.message}") }
        if (player == null) {
            player = TonePlayer(this, listOf(SoundRef.Bundled(TONE), SoundRef.System("default")), 1f) { DiagnosticLog.log(this, "TIMER_SOUND", it) }
                .also { it.start() }
        }
        Ringing.set(ringing.toSet())
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ ringing.toList().forEach { stop(it, plusMinute = false) } }, MAX_RING_MS)
    }

    private fun stop(id: Long, plusMinute: Boolean) {
        ringing -= id
        val store = ClockStore.get(this)
        if (plusMinute) Timers.plusMinute(this, id) else store.updateTimers { list -> list.map { if (it.id == id) it.reset() else it } }
        Ringing.set(ringing.toSet())
        if (ringing.isEmpty()) {
            handler.removeCallbacksAndMessages(null)
            player?.stop()
            player = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
        }
    }

    override fun onDestroy() {
        player?.stop()
        Ringing.set(emptySet())
        super.onDestroy()
    }

    private fun notification(id: Long): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.timer_done_channel), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
            )
        }
        val label = ClockStore.get(this).timers.value.firstOrNull { it.id == id }?.label?.ifBlank { null } ?: getString(R.string.timer_default_label)
        val screen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TimerDoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(getString(R.string.timer_done, label))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .addAction(0, getString(R.string.timer_stop), command(this, ACTION_STOP, id))
            .addAction(0, getString(R.string.timer_plus_minute), command(this, ACTION_PLUS_MINUTE, id))
            .build()
    }

    /** Timers ringing now, for the full screen. */
    object Ringing {
        private val state = MutableStateFlow<Set<Long>>(emptySet())
        val current: StateFlow<Set<Long>> = state

        internal fun set(ids: Set<Long>) {
            state.value = ids
        }
    }

    companion object {
        private const val ACTION_RING = "com.qtekfun.ultimatealarm.timer.RING"
        internal const val ACTION_STOP = "com.qtekfun.ultimatealarm.timer.STOP"
        internal const val ACTION_PLUS_MINUTE = "com.qtekfun.ultimatealarm.timer.RING_PLUS"
        private const val EXTRA_ID = "timer_id"
        private const val CHANNEL = "timer_done"
        private const val NOTIFICATION_ID = 2
        private const val TONE = "timer_done"
        private const val MAX_RING_MS = 10 * 60_000L

        fun ringIntent(context: Context, id: Long): Intent = Intent(context, TimerService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, id)

        internal fun command(context: Context, action: String, id: Long): PendingIntent = PendingIntent.getService(
            context,
            (action.hashCode() * 31 + id).toInt(),
            Intent(context, TimerService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun send(context: Context, action: String, id: Long) {
            context.startService(Intent(context, TimerService::class.java).setAction(action).putExtra(EXTRA_ID, id))
        }
    }
}
