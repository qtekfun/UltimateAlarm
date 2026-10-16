package com.qtekfun.ultimatealarm.feature.holidays

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialCalendarsTest {
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun `madrid city has an official source, other towns do not`() {
        assertEquals("mad/madrid/madrid", OfficialCalendars.forPath("mad/madrid/madrid")?.path)
        assertNull(OfficialCalendars.forPath("mad/madrid/getafe"))
        assertNull(OfficialCalendars.forPath(null))
    }

    @Test
    fun `ics resources are found in the CKAN answer`() {
        val urls = OfficialCalendars.icsUrls(resource("ckan-madrid.json"))
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.startsWith("https://datos.madrid.es/") && it.endsWith(".ics") })
    }

    @Test
    fun `the official 2026 file reads with its own names`() {
        val holidays = IcsParser.parse(resource("madrid-2026.ics"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals(14, holidays.size)
        assertEquals("San Isidro Labrador", holidays.first { it.date == LocalDate.of(2026, 5, 15) }.name)
        assertEquals("Nuestra Señora de la Almudena", holidays.first { it.date == LocalDate.of(2026, 11, 9) }.name)
    }

    @Test
    fun `official days win over the compiled ones`() {
        val official = listOf(com.qtekfun.ultimatealarm.core.scheduling.Holiday(LocalDate.of(2026, 5, 15), "San Isidro Labrador"))
        val compiled = listOf(
            com.qtekfun.ultimatealarm.core.scheduling.Holiday(LocalDate.of(2026, 5, 15), "Fiesta local"),
            com.qtekfun.ultimatealarm.core.scheduling.Holiday(LocalDate.of(2026, 11, 9), "Fiesta local")
        )
        val merged = mergeLocal(official, compiled)
        assertEquals(listOf("San Isidro Labrador", "Fiesta local"), merged.map { it.name })
    }
}
