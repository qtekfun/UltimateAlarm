package com.qtekfun.ultimatealarm.feature.holidays

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NagerParserTest {
    private val es2026 = javaClass.getResource("/nager-ES-2026.json")!!.readText()

    @Test
    fun `national holidays only without a region`() {
        val dates = NagerParser.parse(es2026, null).map { it.date }
        assertTrue(LocalDate.of(2026, 10, 12) in dates)
        assertTrue(LocalDate.of(2026, 1, 6) in dates)
        assertFalse(LocalDate.of(2026, 5, 2) in dates) // Comunidad de Madrid
        assertFalse(LocalDate.of(2026, 2, 28) in dates) // Andalucía
    }

    @Test
    fun `region adds its own holidays`() {
        val madrid = NagerParser.parse(es2026, "ES-MD")
        assertTrue(madrid.any { it.date == LocalDate.of(2026, 5, 2) })
        assertFalse(madrid.any { it.date == LocalDate.of(2026, 2, 28) })
        assertEquals("Fiesta Nacional de España", madrid.first { it.date == LocalDate.of(2026, 10, 12) }.name)
    }

    @Test
    fun `subdivisionCodes, non public types and duplicates`() {
        val body = """
            [
              {"date":"2026-03-19","localName":"San José","global":false,"subdivisionCodes":["ES-VC"],"types":["Public"]},
              {"date":"2026-03-20","localName":"Banco","global":true,"types":["Bank"]},
              {"date":"2026-03-21","name":"Sin nombre local","global":true,"types":["Public","Optional"],"extra":1},
              {"date":"2026-03-21","localName":"Duplicado","global":true,"types":["Public"]}
            ]
        """.trimIndent()
        val holidays = NagerParser.parse(body, "ES-VC")
        assertEquals(listOf(LocalDate.of(2026, 3, 19), LocalDate.of(2026, 3, 21)), holidays.map { it.date })
        assertEquals("Sin nombre local", holidays[1].name)
    }

    @Test(expected = Exception::class)
    fun `malformed json throws`() {
        NagerParser.parse("<html>error</html>", null)
    }
}
