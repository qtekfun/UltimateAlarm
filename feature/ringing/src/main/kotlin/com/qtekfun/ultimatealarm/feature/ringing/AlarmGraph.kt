package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.data.BackupStore
import com.qtekfun.ultimatealarm.core.data.HolidayRepository
import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase

/**
 * Process-wide database and repositories, reachable from receivers and services that run before Hilt-injected code (and
 * before the first unlock). The app's Hilt modules expose these same instances.
 */
object AlarmGraph {
    @Volatile
    private var database: AlarmDatabase? = null

    @Volatile
    private var repository: AlarmRepository? = null

    @Volatile
    private var holidays: HolidayRepository? = null

    fun database(context: Context): AlarmDatabase = database ?: synchronized(this) {
        database ?: AlarmDatabase.create(context.applicationContext).also { database = it }
    }

    fun holidayRepository(context: Context): HolidayRepository = holidays ?: synchronized(this) {
        val app = context.applicationContext
        holidays ?: HolidayRepository(database(app), listener = { reason -> Rescheduler.rescheduleAll(app, reason) }).also { holidays = it }
    }

    fun backupStore(context: Context): BackupStore {
        val app = context.applicationContext
        return BackupStore(database(app), listener = { reason -> Rescheduler.rescheduleAll(app, reason) })
    }

    fun repository(context: Context): AlarmRepository = repository ?: synchronized(this) {
        val app = context.applicationContext
        repository ?: AlarmRepository(
            database(app),
            holidays = { holidayRepository(app).calendar() },
            listener = { reason -> Rescheduler.rescheduleAll(app, reason) }
        ).also { repository = it }
    }
}
