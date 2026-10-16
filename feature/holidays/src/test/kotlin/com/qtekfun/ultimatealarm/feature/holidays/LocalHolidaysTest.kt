package com.qtekfun.ultimatealarm.feature.holidays

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHolidaysTest {
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun `madrid 2026 has its local holidays and the moved regional ones`() {
        val holidays = LocalHolidays.parseHolidays(resource("cn-madrid-2026.json"))
        val dates = holidays.map { it.date }
        assertTrue(LocalDate.of(2026, 5, 15) in dates) // San Isidro
        assertTrue(LocalDate.of(2026, 11, 9) in dates) // La Almudena
        assertTrue(LocalDate.of(2026, 10, 12) in dates)
        assertTrue(LocalDate.of(2026, 12, 7) in dates)
        assertEquals(dates.sorted(), dates)
        assertEquals(dates.toSet().size, dates.size)
    }

    @Test
    fun `scopes without a calendar are merged and local names win`() {
        val body = """
            {"holidays":{"national":[{"date":"2026-01-01","name":"Año Nuevo"}],
             "local":[{"date":"2026-09-08","name":"Virgen del pueblo"}],
             "provincial":[{"date":"2026-01-01","name":"ignored duplicate"}]}}
        """.trimIndent()
        val holidays = LocalHolidays.parseHolidays(body)
        assertEquals(listOf("2026-01-01", "2026-09-08").map(LocalDate::parse), holidays.map { it.date })
        assertEquals("Virgen del pueblo", holidays[1].name)
    }

    @Test
    fun `provinces and municipalities`() {
        assertEquals(listOf(LocalHolidays.Place("madrid", "Madrid")), LocalHolidays.parseProvinces(resource("cn-provinces-mad.json")))
        val towns = LocalHolidays.parseMunicipalities(resource("cn-municipalities-madrid.json"))
        assertEquals(179, towns.size)
        assertTrue(towns.any { it.slug == "madrid" })
        assertEquals(towns.sortedBy { it.sortKey }, towns)
    }

    @Test
    fun `every spanish region has a slug and urls are built from slugs`() {
        assertEquals(19, LocalHolidays.communitySlug.size)
        assertEquals("https://calendariosnacionales.com/es/v1/2026/localidades/mad/madrid/madrid.json", LocalHolidays.holidaysUrl(2026, "mad/madrid/madrid"))
        assertEquals(
            "https://calendariosnacionales.com/es/v1/2026/regiones/mad/provincias/madrid/localidades.json",
            LocalHolidays.municipalitiesUrl(2026, "mad", "madrid")
        )
    }
}
