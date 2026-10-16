package com.qtekfun.ultimatealarm.feature.holidays

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IcsParserTest {
    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2027, 12, 31)

    @Test
    fun `all-day events, date-times, multi-day events and escaping`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260515
            DTEND;VALUE=DATE:20260516
            SUMMARY:San Isidro\, Madrid
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261109T000000Z
            SUMMARY:La Almudena
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260907
            DTEND;VALUE=DATE:20260910
            SUMMARY:Fiestas
              del pueblo
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val holidays = IcsParser.parse(ics, from, to)
        assertEquals(
            listOf("2026-05-15", "2026-09-07", "2026-09-08", "2026-09-09", "2026-11-09").map(LocalDate::parse),
            holidays.map { it.date }
        )
        assertEquals("San Isidro, Madrid", holidays[0].name)
        assertEquals("Fiestas del pueblo", holidays[1].name)
    }

    @Test
    fun `yearly events repeat inside the range, 29 February only in leap years`() {
        val ics = """
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20200515
            RRULE:FREQ=YEARLY
            SUMMARY:San Isidro
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20240229
            RRULE:FREQ=YEARLY;INTERVAL=1
            SUMMARY:Bisiesto
            END:VEVENT
        """.trimIndent()
        val holidays = IcsParser.parse(ics, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31))
        assertEquals(listOf("2026-05-15", "2027-05-15", "2028-02-29", "2028-05-15").map(LocalDate::parse), holidays.map { it.date })
    }

    @Test
    fun `events outside the range or without a date are ignored`() {
        val ics = listOf(
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20250101",
            "SUMMARY:Old",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "SUMMARY:No date",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:garbage",
            "END:VEVENT"
        ).joinToString("\n")
        assertEquals(emptyList<Any>(), IcsParser.parse(ics, from, to))
    }
}
