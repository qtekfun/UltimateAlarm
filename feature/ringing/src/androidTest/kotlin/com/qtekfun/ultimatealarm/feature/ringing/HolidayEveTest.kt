package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.ManualHoliday
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** RF-F7 end to end on the device database: a holiday tomorrow is asked about, "Skip them" moves the alarm a day. */
@RunWith(AndroidJUnit4::class)
class HolidayEveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = AlarmGraph.repository(context)
    private val holidays = AlarmGraph.holidayRepository(context)
    private val tomorrow = LocalDate.now().plusDays(1)

    @Before
    @After
    fun clean() = runBlocking {
        alarms.alarms().forEach { alarms.delete(it.id) }
        holidays.observeManual().first().forEach { holidays.deleteManual(it.id) }
    }

    @Test
    fun holidayTomorrowIsAskedAndSkipMovesTheAlarm() = runBlocking {
        val id = alarms.save(Alarm(time = LocalTime.of(9, 0), schedule = Schedule.Weekly(DayOfWeek.entries.toSet())))
        holidays.saveManual(ManualHoliday(date = tomorrow, name = "Test", yearly = false))

        val eve = SchedulingEngine.holidayEve(alarms.alarms().map { it.spec }, alarms.rules(), tomorrow)
        assertNotNull(eve)
        assertEquals(listOf(id), eve!!.alarmIds)

        val now = ZonedDateTime.now()
        val before = SchedulingEngine.nextOccurrence(alarms.alarm(id)!!.spec, alarms.rules(), now)!!.date
        HolidayEve.answer(context, tomorrow, skip = true)
        val after = SchedulingEngine.nextOccurrence(alarms.alarm(id)!!.spec, alarms.rules(), now)!!.date
        assertEquals(if (before == tomorrow) tomorrow.plusDays(1) else before, after)
        assertEquals(null, SchedulingEngine.holidayEve(alarms.alarms().map { it.spec }, alarms.rules().copy(decisions = emptyList()), tomorrow.plusDays(1)))
    }
}
