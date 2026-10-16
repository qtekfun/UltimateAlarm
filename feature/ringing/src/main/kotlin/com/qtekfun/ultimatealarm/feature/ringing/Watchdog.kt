package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.content.Context
import android.os.UserManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.DiagnosticsStore
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Daily watchdog (02-diseno §4), no constraints: logs what the system says the next alarm clock is against what the app
 * registered, then re-schedules everything so any drift heals itself.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val registered = DiagnosticsStore(applicationContext).registered
        val expected = registered.values.minOrNull()
        val system = applicationContext.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        val verdict = when {
            expected == null && system == null -> "ok: nothing scheduled"
            expected == system -> "ok"
            else -> "MISMATCH"
        }
        DiagnosticLog.log(
            applicationContext,
            "WATCHDOG",
            "$verdict expected=${expected?.let(Instant::ofEpochMilli)} system=${system?.let(Instant::ofEpochMilli)} alarms=${registered.size}"
        )
        Rescheduler.rescheduleAll(applicationContext, "watchdog")
        return Result.success()
    }
}

object Watchdog {
    private const val NAME = "alarm-watchdog"

    /** WorkManager keeps its state in credential-protected storage: only touch it once the user has unlocked. */
    fun ensureScheduled(context: Context) {
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
