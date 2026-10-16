package com.qtekfun.ultimatealarm.feature.backup

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.BackupContent
import com.qtekfun.ultimatealarm.core.data.ManualHoliday
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.DayOverride
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.HolidayOverride
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {
    private val day = LocalDate.of(2026, 10, 19)
    private val content = BackupContent(
        alarms = listOf(
            Alarm(id = 1, time = LocalTime.of(6, 45), label = "Trabajo", sound = "bundled:soft_pad", challenge = "math:2:3", preNotifyMinutes = 60),
            Alarm(id = 2, schedule = Schedule.Cycle(day, listOf(true, true, false, false)), backupAfterMinutes = null, maxSnoozes = Alarm.UNLIMITED_SNOOZES),
            Alarm(id = 3, kind = AlarmKind.ONE_SHOT, oneShotDate = day, respectHolidays = false)
        ),
        overrides = listOf(DayOverride(1, day, OverrideMode.FORCE_OFF)),
        skips = listOf(SkipNext(2, day)),
        vacations = listOf(Vacation(0, day, day.plusDays(5), null), Vacation(0, day, day, setOf(1L, 2L))),
        decisions = listOf(HolidayDecision(day, null, true), HolidayDecision(day, 1, false)),
        manualHolidays = listOf(ManualHoliday(date = LocalDate.of(2026, 5, 15), name = "San Isidro", yearly = true)),
        holidayOverrides = listOf(HolidayOverride(LocalDate.of(2026, 1, 6), false))
    )

    @Test
    fun `round trip keeps every field and audio`() {
        val file = BackupFormat.toFile(content, 1_000, "0.8.0", mapOf("country" to "ES"), byteArrayOf(1, 2, 3))
        val bytes = BackupFormat.pack(file, mapOf("tono.mp3" to byteArrayOf(9, 8, 7)))
        val parsed = BackupFormat.unpack(bytes)
        assertEquals(content.copy(manualHolidays = content.manualHolidays.map { it.copy(id = 0) }), parsed.content)
        assertEquals(3, parsed.alarmCount)
        assertEquals(mapOf("country" to "ES"), parsed.file.settings)
        assertArrayEquals(byteArrayOf(1, 2, 3), BackupFormat.credentials(parsed.file))
        assertArrayEquals(byteArrayOf(9, 8, 7), parsed.audio.getValue("tono.mp3"))
        assertArrayEquals("same content, same bytes", bytes, BackupFormat.pack(file, mapOf("tono.mp3" to byteArrayOf(9, 8, 7))))
    }

    @Test
    fun `no credentials without a password`() {
        val parsed = BackupFormat.unpack(BackupFormat.pack(BackupFormat.toFile(BackupContent(), 1, "x", emptyMap(), null)))
        assertNull(BackupFormat.credentials(parsed.file))
    }

    @Test
    fun `newer schema, missing json and garbage are refused`() {
        val newer = BackupFormat.toFile(BackupContent(), 1, "x", emptyMap(), null).copy(schemaVersion = 99)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.unpack(BackupFormat.pack(newer)) }
        val empty = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("other.txt"))
                it.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.unpack(empty) }
        assertThrows(Exception::class.java) { BackupFormat.unpack(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `archive paths never escape`() {
        assertEquals("evil.sh", BackupFormat.safeName("../../evil.sh"))
        assertEquals("a_b.mp3", BackupFormat.safeName("dir\\a b.mp3"))
        assertEquals("audio", BackupFormat.safeName("x/"))
    }
}
