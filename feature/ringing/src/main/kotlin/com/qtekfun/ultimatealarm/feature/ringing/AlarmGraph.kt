package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.data.HolidaySource
import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.scheduling.HolidayCalendar

/**
 * Process-wide database and repository, reachable from receivers and services that run before Hilt-injected code (and
 * before the first unlock). The app's Hilt modules expose these same instances.
 */
object AlarmGraph {
    @Volatile
    private var database: AlarmDatabase? = null

    @Volatile
    private var repository: AlarmRepository? = null

    @Volatile
    var holidays: HolidaySource = HolidaySource { HolidayCalendar.NONE }

    fun database(context: Context): AlarmDatabase = database ?: synchronized(this) {
        database ?: AlarmDatabase.create(context.applicationContext).also { database = it }
    }

    fun repository(context: Context): AlarmRepository = repository ?: synchronized(this) {
        val app = context.applicationContext
        repository ?: AlarmRepository(
            database(app),
            holidays = { holidays.calendar() },
            listener = { reason -> Rescheduler.rescheduleAll(app, reason) }
        ).also { repository = it }
    }
}
