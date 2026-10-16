package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.TestAlarmStore
import com.qtekfun.ultimatealarm.core.scheduling.nextDailyOccurrence
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** The spike's scheduling: one daily test alarm and a one-minute autotest, rebuilt from device-protected storage. */
object TestAlarms {
    /** Re-registers everything with AlarmManager. Called on boot, time and zone changes, updates and every edit. */
    fun rescheduleAll(context: Context, reason: String) {
        val store = TestAlarmStore(context)
        DiagnosticLog.log(context, "RESCHEDULE", "reason=$reason zone=${ZonedDateTime.now().zone}")
        if (store.enabled) {
            scheduleNextTest(context, store)
        } else {
            AlarmScheduler.cancel(context, SpikeAlarm.TEST.id)
            store.scheduledAt = 0
        }
        val autotestAt = store.autotestAt
        if (autotestAt > System.currentTimeMillis()) {
            AlarmScheduler.schedule(context, SpikeAlarm.AUTOTEST.id, autotestAt)
        }
    }

    fun setTestAlarm(context: Context, time: LocalTime, enabled: Boolean) {
        val store = TestAlarmStore(context)
        store.time = time
        store.enabled = enabled
        rescheduleAll(context, "edit")
    }

    /** Schedules the next occurrence of the daily test alarm strictly after now. */
    fun scheduleNextTest(context: Context, store: TestAlarmStore = TestAlarmStore(context)) {
        val next = nextDailyOccurrence(store.time, ZonedDateTime.now()).toInstant().toEpochMilli()
        store.scheduledAt = next
        AlarmScheduler.schedule(context, SpikeAlarm.TEST.id, next)
    }

    /** RF-O2: a real alarm in one minute; the user turns the screen off and waits. */
    fun startAutotest(context: Context): Instant {
        val at = System.currentTimeMillis() + AUTOTEST_DELAY_MS
        TestAlarmStore(context).autotestAt = at
        DiagnosticLog.log(context, "AUTOTEST_START", "at=${Instant.ofEpochMilli(at)}", SpikeAlarm.AUTOTEST.id)
        AlarmScheduler.schedule(context, SpikeAlarm.AUTOTEST.id, at)
        return Instant.ofEpochMilli(at)
    }

    fun snooze(context: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * MINUTE_MS
        AlarmScheduler.schedule(context, SpikeAlarm.SNOOZE.id, at)
    }

    private const val MINUTE_MS = 60_000L
    private const val AUTOTEST_DELAY_MS = MINUTE_MS
}
