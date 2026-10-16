package com.qtekfun.ultimatealarm.core.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
internal val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

internal fun date(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

internal fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, zone: ZoneId = MADRID): ZonedDateTime = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

internal fun weekly(id: Long = 1, h: Int = 7, min: Int = 0, days: Set<DayOfWeek> = WORKDAYS, respectHolidays: Boolean = true) =
    AlarmSpec(id = id, time = LocalTime.of(h, min), schedule = Schedule.Weekly(days), respectHolidays = respectHolidays)

internal fun holidays(vararg entries: Pair<LocalDate, String>) = EffectiveHolidays(api = entries.map { Holiday(it.first, it.second) })

internal fun next(alarm: AlarmSpec, now: ZonedDateTime, rules: RuleSet = RuleSet()) = SchedulingEngine.nextOccurrence(alarm, rules, now)
