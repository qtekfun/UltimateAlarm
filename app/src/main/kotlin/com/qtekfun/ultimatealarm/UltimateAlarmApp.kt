package com.qtekfun.ultimatealarm

import android.app.Application
import com.qtekfun.ultimatealarm.feature.ringing.Rescheduler
import com.qtekfun.ultimatealarm.feature.ringing.Watchdog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class UltimateAlarmApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Off the main thread: make AlarmManager match the database on every start, and keep the daily watchdog queued.
        scope.launch {
            Rescheduler.rescheduleAll(this@UltimateAlarmApp, "app_start")
            Watchdog.ensureScheduled(this@UltimateAlarmApp)
        }
    }
}
