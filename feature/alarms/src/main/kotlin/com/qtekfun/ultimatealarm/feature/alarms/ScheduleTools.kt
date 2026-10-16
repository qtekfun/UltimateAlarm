package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** RF-P2 presets of shift patterns. */
enum class ShiftPreset(val on: Int, val off: Int) {
    FOUR_FOUR(4, 4),
    FIVE_TWO(5, 2),
    SIX_THREE(6, 3),
    TWO_TWO(2, 2);

    fun mask(): List<Boolean> = List(on) { true } + List(off) { false }
}

object ScheduleTools {
    const val MIN_CYCLE = 2
    const val MAX_CYCLE = 28
    private const val DAYS_PER_WEEK = 7L

    /** A shift [preset] whose first working day is [anchor]. */
    fun shift(preset: ShiftPreset, anchor: LocalDate): Schedule.Cycle = Schedule.Cycle(anchor, preset.mask())

    /**
     * Alternate weeks (RF-P2: "semanas alternas = ciclo 14"): rings on [days] of the week containing [anchorWeek], not on
     * the following week, and so on. The anchor is moved to that week's Monday.
     */
    fun alternateWeeks(days: Set<DayOfWeek>, anchorWeek: LocalDate): Schedule.Cycle {
        val monday = anchorWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mask = List((DAYS_PER_WEEK * 2).toInt()) { i -> i < DAYS_PER_WEEK && monday.plusDays(i.toLong()).dayOfWeek in days }
        return Schedule.Cycle(monday, mask)
    }

    /** Custom cycle of [length] days keeping what fits of [mask]. */
    fun resize(mask: List<Boolean>, length: Int): List<Boolean> {
        val size = length.coerceIn(MIN_CYCLE, MAX_CYCLE)
        return List(size) { mask.getOrElse(it) { false } }
    }

    /** RF-P3: tapping a day cycles "según patrón" → force on → force off → "según patrón". */
    fun nextOverride(current: OverrideMode?): OverrideMode? = when (current) {
        null -> OverrideMode.FORCE_ON
        OverrideMode.FORCE_ON -> OverrideMode.FORCE_OFF
        OverrideMode.FORCE_OFF -> null
    }

    /** Weeks of [month], Monday first, `null` for the padding days of the first and last weeks. */
    fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val days = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val padded = days + List((DAYS_PER_WEEK.toInt() - days.size % DAYS_PER_WEEK.toInt()) % DAYS_PER_WEEK.toInt()) { null }
        return padded.chunked(DAYS_PER_WEEK.toInt())
    }
}
