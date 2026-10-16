package com.qtekfun.ultimatealarm.feature.holidays

import com.qtekfun.ultimatealarm.core.scheduling.Holiday
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Minimal iCalendar (RFC 5545) reader for public holiday calendars: every VEVENT becomes one holiday per day it covers,
 * named by its SUMMARY. Handles all-day dates and date-times (the date part is used), multi-day events (DTEND is
 * exclusive), folded lines, escaped text and `RRULE:FREQ=YEARLY`, expanded over [from]..[to].
 */
object IcsParser {
    fun parse(text: String, from: LocalDate, to: LocalDate): List<Holiday> {
        val holidays = mutableListOf<Holiday>()
        var event: MutableMap<String, String>? = null
        for (line in unfold(text)) {
            when {
                line.trim() == "BEGIN:VEVENT" -> event = mutableMapOf()
                line.trim() == "END:VEVENT" -> {
                    event?.let { holidays += expand(it, from, to) }
                    event = null
                }
                event != null -> {
                    val colon = line.indexOf(':')
                    if (colon > 0) event[line.substring(0, colon).substringBefore(';').uppercase()] = line.substring(colon + 1)
                }
            }
        }
        return holidays.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.distinctBy { it.date }.sortedBy { it.date }
    }

    private fun expand(event: Map<String, String>, from: LocalDate, to: LocalDate): List<Holiday> {
        val start = event["DTSTART"]?.let(::date) ?: return emptyList()
        val end = event["DTEND"]?.let(::date)?.takeIf { it.isAfter(start) } ?: start.plusDays(1)
        val name = unescape(event["SUMMARY"].orEmpty())
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }.take(MAX_EVENT_DAYS).toList()
        val yearly = event["RRULE"]?.split(';')?.any { it.equals("FREQ=YEARLY", ignoreCase = true) } == true
        if (!yearly) return days.map { Holiday(it, name) }
        return (maxOf(from.year, start.year)..to.year).flatMap { year ->
            days.mapNotNull { day -> runCatching { day.withYear(year + (day.year - start.year)) }.getOrNull()?.takeIf { it.dayOfMonth == day.dayOfMonth } }
                .map { Holiday(it, name) }
        }
    }

    private fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(DATE_LENGTH), BASIC_DATE) }.getOrNull()

    /** RFC 5545 §3.1: a line starting with a space or tab continues the previous one. */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").split('\n').forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + raw.substring(1)
            } else if (raw.isNotBlank()) {
                lines += raw
            }
        }
        return lines
    }

    private fun unescape(value: String) = value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private const val DATE_LENGTH = 8
    private const val MAX_EVENT_DAYS = 31
}
