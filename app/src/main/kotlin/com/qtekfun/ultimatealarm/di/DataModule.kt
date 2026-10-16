package com.qtekfun.ultimatealarm.di

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.data.HolidayRepository
import com.qtekfun.ultimatealarm.feature.ringing.AlarmGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The same repository the receivers and the ringing service use (AlarmGraph), so every write re-schedules. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun alarmRepository(@ApplicationContext context: Context): AlarmRepository = AlarmGraph.repository(context)

    @Provides
    @Singleton
    fun holidayRepository(@ApplicationContext context: Context): HolidayRepository = AlarmGraph.holidayRepository(context)
}
