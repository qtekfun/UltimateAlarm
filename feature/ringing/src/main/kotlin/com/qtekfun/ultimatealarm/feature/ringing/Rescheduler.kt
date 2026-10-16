package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.DiagnosticsStore
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Makes AlarmManager match the database: one `setAlarmClock` per alarm at its next occurrence (02-diseno §4). Called on
 * boot (also locked), time and zone changes, app updates, every database change, after firing and by the watchdog.
 */
object Rescheduler {
    private val mutex = Mutex()

    suspend fun rescheduleAll(context: Context, reason: String) = mutex.withLock {
        val repository = AlarmGraph.repository(context)
        val store = DiagnosticsStore(context)
        val alarms = repository.alarms()
        val rules = repository.rules()
        val now = ZonedDateTime.now()
        val plan = planSchedule(alarms, rules, now, store.registered.keys)

        plan.cancel.forEach { AlarmScheduler.cancel(context, it) }
        plan.register.forEach { (id, at) -> AlarmScheduler.schedule(context, id, at) }
        plan.missed.forEach { id ->
            repository.markMissed(id)
            alarms.firstOrNull { it.id == id }?.let { Notifications.missed(context, it) }
            DiagnosticLog.log(context, "MISSED", "one-shot time passed without ringing", id)
        }
        if (plan.staleSkips.isNotEmpty()) repository.dropSkips(plan.staleSkips)
        store.registered = plan.register

        val autotestAt = store.autotestAt
        if (autotestAt > System.currentTimeMillis()) AlarmScheduler.schedule(context, AlarmScheduler.AUTOTEST_ID, autotestAt)

        DiagnosticLog.log(
            context,
            "RESCHEDULE",
            "reason=$reason zone=${now.zone} registered=${plan.register.size} cancelled=${plan.cancel.size} " +
                "next=${plan.register.values.minOrNull()?.let { Instant.ofEpochMilli(it) } ?: "none"}"
        )
    }

    /** RF-O2: a real alarm in one minute; the user turns the screen off and waits. */
    fun startAutotest(context: Context): Instant {
        val at = System.currentTimeMillis() + AUTOTEST_DELAY_MS
        DiagnosticsStore(context).autotestAt = at
        DiagnosticLog.log(context, "AUTOTEST_START", "at=${Instant.ofEpochMilli(at)}", AlarmScheduler.AUTOTEST_ID)
        AlarmScheduler.schedule(context, AlarmScheduler.AUTOTEST_ID, at)
        return Instant.ofEpochMilli(at)
    }

    private const val AUTOTEST_DELAY_MS = 60_000L
}
