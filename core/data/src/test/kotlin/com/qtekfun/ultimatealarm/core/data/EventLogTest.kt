package com.qtekfun.ultimatealarm.core.data

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    private val dir: File = Files.createTempDirectory("eventlog").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `events are read back in order`() {
        val log = EventLog(dir)
        val first = Event(Instant.ofEpochMilli(1_000), 1, "SCHEDULED", "at 07:00")
        val second = Event(Instant.ofEpochMilli(2_000), null, "BOOT", "")
        log.append(first)
        log.append(second)
        assertEquals(listOf(first, second), log.read())
    }

    @Test
    fun `tabs, newlines and backslashes survive`() {
        val log = EventLog(dir)
        val event = Event(Instant.ofEpochMilli(5), 2, "ERROR", "line1\nline2\tcol \\ end")
        log.append(event)
        assertEquals(listOf(event), log.read())
    }

    @Test
    fun `log rotates once and keeps the previous file`() {
        val log = EventLog(dir, maxBytes = 64)
        repeat(20) { log.append(Event(Instant.ofEpochMilli(it.toLong()), null, "TICK", "n=$it")) }
        val events = log.read()
        assertTrue(events.size in 2 until 20)
        assertEquals("n=19", events.last().detail)
        assertTrue(File(dir, "${EventLog.FILE_NAME}.1").exists())
    }

    @Test
    fun `export is human readable`() {
        val log = EventLog(dir)
        log.append(Event(Instant.parse("2026-10-16T05:00:00Z"), 3, "FIRED", "delay=120ms"))
        val text = log.export(ZoneOffset.ofHours(2))
        assertEquals("# UltimateAlarm event log\n2026-10-16T07:00:00+02:00\t3\tFIRED\tdelay=120ms\n", text)
    }

    @Test
    fun `clear removes everything`() {
        val log = EventLog(dir, maxBytes = 16)
        repeat(5) { log.append(Event(Instant.ofEpochMilli(0), null, "X", "y")) }
        log.clear()
        assertEquals(emptyList<Event>(), log.read())
    }

    @Test
    fun `malformed lines are skipped`() {
        assertNull(EventLog.decode("garbage"))
        assertNull(EventLog.decode("notanumber\t\tA\tB"))
        dir.mkdirs()
        File(dir, EventLog.FILE_NAME).writeText("garbage\n" + EventLog.encode(Event(Instant.ofEpochMilli(9), null, "OK", "")) + "\n")
        assertEquals(1, EventLog(dir).read().size)
    }
}
