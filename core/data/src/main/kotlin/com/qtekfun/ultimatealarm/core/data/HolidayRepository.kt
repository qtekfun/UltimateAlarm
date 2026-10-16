package com.qtekfun.ultimatealarm.core.data

import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.data.db.HolidayEntity
import com.qtekfun.ultimatealarm.core.data.db.HolidayOverrideEntity
import com.qtekfun.ultimatealarm.core.data.db.ManualHolidayEntity
import com.qtekfun.ultimatealarm.core.scheduling.EffectiveHolidays
import com.qtekfun.ultimatealarm.core.scheduling.Holiday
import com.qtekfun.ultimatealarm.core.scheduling.HolidayCalendar
import com.qtekfun.ultimatealarm.core.scheduling.HolidayOverride
import com.qtekfun.ultimatealarm.core.scheduling.YearlyHoliday
import java.time.LocalDate
import java.time.MonthDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Where a holiday comes from (RF-F6). */
enum class HolidaySourceType { API, ICS, MANUAL }

/** One row of the yearly list in Settings → Holidays (RF-F6): origin and whether the user cancelled it (RF-F5). */
data class HolidayEntry(val date: LocalDate, val name: String, val source: HolidaySourceType, val cancelled: Boolean, val manualId: Long? = null)

data class ManualHoliday(val id: Long = 0, val date: LocalDate, val name: String, val yearly: Boolean)

/**
 * Holidays: automatic ones (Nager.Date, public ICS) cached per source, manual ones and overrides. [calendar] is what the
 * scheduling engine sees: effective holidays = (automatic ∪ manual) with overrides applied (§3). Every change tells the
 * listener so alarms are re-scheduled.
 */
class HolidayRepository(db: AlarmDatabase, private val listener: AlarmsChangedListener = AlarmsChangedListener { }) {
    private val dao = db.holidayDao()
    private val user = db.userHolidayDao()

    suspend fun calendar(): HolidayCalendar = build(dao.holidays(), user.manual(), user.overrides())

    fun observeCalendar(): Flow<HolidayCalendar> = combine(dao.observeHolidays(), user.observeManual(), user.observeOverrides(), ::build)

    /** Effective and cancelled holidays of [year], sorted, with their origin. */
    fun observeYear(year: Int): Flow<List<HolidayEntry>> =
        combine(dao.observeHolidays(), user.observeManual(), user.observeOverrides()) { auto, manual, overrides ->
            val cancelled = overrides.filter { !it.isHoliday }.map { it.date }.toSet()
            val autoEntries = auto.filter { LocalDate.ofEpochDay(it.date).year == year }.map {
                HolidayEntry(LocalDate.ofEpochDay(it.date), it.name, enumValueOf(it.source), it.date in cancelled)
            }
            val manualEntries = manual.mapNotNull { row ->
                val date = LocalDate.ofEpochDay(row.date)
                val inYear = if (row.yearly) MonthDay.from(date).takeIf { it.isValidYear(year) }?.atYear(year) else date.takeIf { it.year == year }
                inYear?.let { HolidayEntry(it, row.name, HolidaySourceType.MANUAL, it.toEpochDay() in cancelled, row.id) }
            }
            (autoEntries + manualEntries).sortedWith(compareBy({ it.date }, { it.source }))
        }

    fun observeManual(): Flow<List<ManualHoliday>> = user.observeManual().map { rows -> rows.map { it.toManual() } }

    /** Replaces the holidays of [source] between [from] and [to] (a refresh). Nothing changes if [holidays] is empty. */
    suspend fun replace(source: HolidaySourceType, from: LocalDate, to: LocalDate, holidays: List<Holiday>) {
        if (holidays.isEmpty()) return
        dao.deleteRange(source.name, from.toEpochDay(), to.toEpochDay())
        dao.insert(holidays.map { HolidayEntity(it.date.toEpochDay(), source.name, it.name) })
        listener.onAlarmsChanged("holidays_${source.name.lowercase()}")
    }

    /** Whether [source] has anything cached for the days of [year]. */
    suspend fun hasYear(source: HolidaySourceType, year: Int): Boolean =
        dao.count(source.name, LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay()) > 0

    /** Drops a whole source, e.g. when the region or the ICS address changes. */
    suspend fun clear(source: HolidaySourceType) {
        dao.deleteSource(source.name)
        listener.onAlarmsChanged("holidays_cleared")
    }

    suspend fun saveManual(holiday: ManualHoliday) {
        user.upsertManual(ManualHolidayEntity(holiday.id, holiday.date.toEpochDay(), holiday.name, holiday.yearly))
        listener.onAlarmsChanged("holiday_manual")
    }

    suspend fun deleteManual(id: Long) {
        user.deleteManual(id)
        listener.onAlarmsChanged("holiday_manual")
    }

    /** RF-F5: `cancelled = true` makes [date] a normal day for the user; `false` restores it. */
    suspend fun setCancelled(date: LocalDate, cancelled: Boolean) {
        if (cancelled) user.putOverride(HolidayOverrideEntity(date.toEpochDay(), isHoliday = false, name = "")) else user.deleteOverride(date.toEpochDay())
        listener.onAlarmsChanged("holiday_override")
    }

    private fun build(auto: List<HolidayEntity>, manual: List<ManualHolidayEntity>, overrides: List<HolidayOverrideEntity>): HolidayCalendar =
        EffectiveHolidays(
            api = auto.sortedBy { it.source != HolidaySourceType.API.name }.map { Holiday(LocalDate.ofEpochDay(it.date), it.name) }.distinctBy { it.date },
            manual = manual.filterNot { it.yearly }.map { Holiday(LocalDate.ofEpochDay(it.date), it.name) },
            yearly = manual.filter { it.yearly }.map { YearlyHoliday(MonthDay.from(LocalDate.ofEpochDay(it.date)), it.name) },
            overrides = overrides.map { HolidayOverride(LocalDate.ofEpochDay(it.date), it.isHoliday, it.name) }
        )

    private fun ManualHolidayEntity.toManual() = ManualHoliday(id, LocalDate.ofEpochDay(date), name, yearly)
}
