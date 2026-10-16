package com.qtekfun.ultimatealarm.core.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The base pattern of a recurring alarm: which days it rings before exceptions, vacations, skips and holidays. */
sealed interface Schedule {
    /** True when the base pattern alone says the alarm rings on [date]. */
    fun isScheduledOn(date: LocalDate): Boolean

    /** Rings on the given days of the week. */
    data class Weekly(val days: Set<DayOfWeek>) : Schedule {
        override fun isScheduledOn(date: LocalDate): Boolean = date.dayOfWeek in days
    }

    /**
     * Shift pattern: a cycle of `mask.size` days starting at [anchor], `mask[i]` telling whether day `i` rings.
     * Works before the anchor too (the cycle extends backwards).
     */
    data class Cycle(val anchor: LocalDate, val mask: List<Boolean>) : Schedule {
        init {
            require(mask.isNotEmpty()) { "A cycle needs at least one day" }
        }

        override fun isScheduledOn(date: LocalDate): Boolean {
            val offset = ChronoUnit.DAYS.between(anchor, date)
            return mask[Math.floorMod(offset, mask.size.toLong()).toInt()]
        }
    }
}
