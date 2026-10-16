package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalDate

/** Answers "is this an effective holiday?" for the engine. */
fun interface HolidayCalendar {
    fun holidayOn(date: LocalDate): Holiday?

    companion object {
        val NONE = HolidayCalendar { null }
    }
}

/** A user's override of one date (RF-F5): `isHoliday = false` removes a holiday, `true` adds one. */
data class HolidayOverride(val date: LocalDate, val isHoliday: Boolean, val name: String = "")

/**
 * Effective holidays = (API ∪ manual) with the overrides applied (02-diseno §3). A yearly manual holiday on 29 February
 * only exists in leap years. When several sources name the same day, the manual name wins over the API one.
 */
class EffectiveHolidays(
    api: List<Holiday> = emptyList(),
    manual: List<Holiday> = emptyList(),
    private val yearly: List<YearlyHoliday> = emptyList(),
    overrides: List<HolidayOverride> = emptyList()
) : HolidayCalendar {
    private val fixed: Map<LocalDate, Holiday> = (api + manual).associateBy { it.date }
    private val overrideByDate: Map<LocalDate, HolidayOverride> = overrides.associateBy { it.date }

    override fun holidayOn(date: LocalDate): Holiday? {
        overrideByDate[date]?.let { override ->
            return if (override.isHoliday) fixed[date] ?: yearlyOn(date) ?: Holiday(date, override.name) else null
        }
        return fixed[date] ?: yearlyOn(date)
    }

    private fun yearlyOn(date: LocalDate): Holiday? = yearly.firstOrNull { it.monthDay.isValidYear(date.year) && it.monthDay.atYear(date.year) == date }
        ?.let { Holiday(date, it.name) }
}
