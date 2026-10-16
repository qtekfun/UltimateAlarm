package com.qtekfun.ultimatealarm.core.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {
    @Test
    fun `round trips, also with separators in the values`() {
        listOf(
            Challenge.Math(2, 5),
            Challenge.Code("https://example.org/a|b%c", "Baño"),
            Challenge.Code("4006381333931"),
            Challenge.Nfc("04A224B2C35E80", "Cocina"),
            Challenge.Nfc("0A0B")
        ).forEach { assertEquals(it, Challenge.decode(it.encode())) }
    }

    @Test
    fun `invalid text is no challenge and maths is clamped`() {
        assertNull(Challenge.decode(null))
        assertNull(Challenge.decode("math:x"))
        assertNull(Challenge.decode("qr:"))
        assertNull(Challenge.decode("nfc:|alias"))
        assertNull(Challenge.decode("other"))
        assertEquals(Challenge.Math(3, 10), Challenge.decode("math:9:99"))
        assertEquals("math:1:1", Challenge.Math(0, 0).encode())
    }

    @Test
    fun `missing hardware degrades to maths`() {
        val code = Challenge.Code("x")
        val nfc = Challenge.Nfc("01")
        assertEquals(code, Challenge.effective(code, hasCamera = true, hasNfc = false))
        assertEquals(Challenge.Math(), Challenge.effective(code, hasCamera = false, hasNfc = true))
        assertEquals(nfc, Challenge.effective(nfc, hasCamera = false, hasNfc = true))
        assertEquals(Challenge.Math(), Challenge.effective(nfc, hasCamera = true, hasNfc = false))
        assertNull(Challenge.effective(null, hasCamera = false, hasNfc = false))
        assertEquals(Challenge.Math(3, 2), Challenge.effective(Challenge.Math(3, 2), hasCamera = false, hasNfc = false))
    }

    @Test
    fun `problems have the right answer for every difficulty`() {
        val random = Random(42)
        repeat(300) {
            val problem = MathProblems.generate(it % 3 + 1, random)
            val parts = problem.text.split(" ")
            val expected = when {
                parts.size == 5 -> parts[0].toInt() * parts[2].toInt() + parts[4].toInt()
                parts[1] == "+" -> parts[0].toInt() + parts[2].toInt()
                parts[1] == "−" -> parts[0].toInt() - parts[2].toInt()
                else -> parts[0].toInt() * parts[2].toInt()
            }
            assertEquals(problem.text, expected, problem.answer)
            assertTrue(problem.answer >= 0)
        }
    }

    @Test
    fun `answer check tolerates spaces only`() {
        val problem = MathProblem("2 + 2", 4)
        assertTrue(MathProblems.check(problem, " 4 "))
        assertFalse(MathProblems.check(problem, "5"))
        assertFalse(MathProblems.check(problem, "four"))
    }
}
