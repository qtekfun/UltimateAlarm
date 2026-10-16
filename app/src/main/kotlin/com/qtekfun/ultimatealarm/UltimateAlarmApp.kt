package com.qtekfun.ultimatealarm

import android.app.Application
import android.os.UserManager
import com.qtekfun.ultimatealarm.feature.holidays.HolidayGraph
import com.qtekfun.ultimatealarm.feature.holidays.HolidayRefresher
import com.qtekfun.ultimatealarm.feature.holidays.HolidayWork
import com.qtekfun.ultimatealarm.feature.ringing.AlarmGraph
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
        HolidayGraph.repository = AlarmGraph::holidayRepository
        scope.launch {
            // Holidays from the embedded snapshot first, so the very first holiday eve works without network (RF-F3).
            runCatching { HolidayRefresher(this@UltimateAlarmApp, AlarmGraph.holidayRepository(this@UltimateAlarmApp)).seedFromSnapshot() }
            Rescheduler.rescheduleAll(this@UltimateAlarmApp, "app_start")
            Watchdog.ensureScheduled(this@UltimateAlarmApp)
            if (getSystemService(UserManager::class.java).isUserUnlocked) HolidayWork.ensureScheduled(this@UltimateAlarmApp)
        }
    }
}
