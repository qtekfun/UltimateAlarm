package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTimesTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `normal day keeps the wall-clock time`() {
        val at = atLocalTime(LocalDate.of(2026, 10, 16), LocalTime.of(7, 0), madrid)
        assertEquals(LocalTime.of(7, 0), at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), at.offset)
    }

    @Test
    fun `time skipped by spring forward rings at the end of the gap`() {
        // 2026-03-29: 02:00 CET jumps to 03:00 CEST in Madrid.
        val at = atLocalTime(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), madrid)
        assertEquals(LocalTime.of(3, 0), at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), at.offset)
    }

    @Test
    fun `time repeated by fall back rings the first time`() {
        // 2026-10-25: 03:00 CEST falls back to 02:00 CET; 02:30 happens twice.
        val at = atLocalTime(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), madrid)
        assertEquals(LocalTime.of(2, 30), at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), at.offset)
    }

    @Test
    fun `next daily occurrence is today when still ahead`() {
        val now = ZonedDateTime.of(2026, 10, 16, 6, 0, 0, 0, madrid)
        assertEquals(ZonedDateTime.of(2026, 10, 16, 7, 0, 0, 0, madrid), nextDailyOccurrence(LocalTime.of(7, 0), now))
    }

    @Test
    fun `next daily occurrence is tomorrow when already past`() {
        val now = ZonedDateTime.of(2026, 10, 16, 8, 0, 0, 0, madrid)
        assertEquals(ZonedDateTime.of(2026, 10, 17, 7, 0, 0, 0, madrid), nextDailyOccurrence(LocalTime.of(7, 0), now))
    }

    @Test
    fun `next daily occurrence is strictly after now`() {
        val now = ZonedDateTime.of(2026, 10, 16, 7, 0, 0, 0, madrid)
        assertEquals(ZonedDateTime.of(2026, 10, 17, 7, 0, 0, 0, madrid), nextDailyOccurrence(LocalTime.of(7, 0), now))
    }

    @Test
    fun `next daily occurrence across the spring gap`() {
        val now = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, madrid)
        val next = nextDailyOccurrence(LocalTime.of(2, 30), now)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 0, 0, 0, madrid), next)
    }

    @Test
    fun `next daily occurrence follows the zone of now`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 10, 16, 6, 0, 0, 0, tokyo)
        assertEquals(ZonedDateTime.of(2026, 10, 16, 7, 0, 0, 0, tokyo), nextDailyOccurrence(LocalTime.of(7, 0), now))
    }
}
