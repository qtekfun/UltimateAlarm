package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The instant at which wall-clock [time] happens on [date] in [zone], following the DST rules of the design (§3):
 * - a time skipped by a spring-forward gap rings at the first valid instant after it (the end of the gap);
 * - a time repeated by an autumn overlap rings at its first occurrence (the earlier offset).
 */
fun atLocalTime(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime {
    val local = date.atTime(time)
    val rules = zone.rules
    if (rules.getValidOffsets(local).isEmpty()) {
        val gap = rules.getTransition(local)
        return gap.instant.atZone(zone)
    }
    // ZonedDateTime.of keeps the earlier offset in an overlap.
    return ZonedDateTime.of(local, zone)
}

/**
 * Next instant strictly after [now] at which wall-clock [time] happens, today or on a later day, in the zone of [now].
 * Used by the spike's daily test alarm; the full engine (Phase 2) builds on [atLocalTime].
 */
fun nextDailyOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val zone = now.zone
    var date = now.toLocalDate()
    while (true) {
        val candidate = atLocalTime(date, time, zone)
        if (candidate.isAfter(now)) return candidate
        date = date.plusDays(1)
    }
}
