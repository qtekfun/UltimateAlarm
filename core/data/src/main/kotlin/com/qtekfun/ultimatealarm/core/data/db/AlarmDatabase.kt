package com.qtekfun.ultimatealarm.core.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatealarm.core.data.DeviceStorage

@Database(
    entities = [
        AlarmEntity::class,
        DayOverrideEntity::class,
        SkipEntity::class,
        VacationEntity::class,
        VacationAlarmEntity::class,
        HolidayDecisionEntity::class,
        HolidayEntity::class,
        ManualHolidayEntity::class,
        HolidayOverrideEntity::class
    ],
    version = AlarmDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun overrideDao(): OverrideDao

    abstract fun skipDao(): SkipDao

    abstract fun vacationDao(): VacationDao

    abstract fun decisionDao(): DecisionDao

    abstract fun holidayDao(): HolidayDao

    abstract fun userHolidayDao(): UserHolidayDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "alarms.db"

        /**
         * The database lives in device-protected storage, so alarms can be read and re-scheduled after a reboot before the
         * user unlocks the phone (RNF-1, docs/spec/DECISIONS.md D-12).
         */
        fun create(context: Context): AlarmDatabase = Room.databaseBuilder<AlarmDatabase>(DeviceStorage.context(context), FILE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
}
