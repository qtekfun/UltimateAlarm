package com.qtekfun.ultimatealarm.feature.onboarding

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuidesTest {
    private val file = OemGuides.parse(File("src/main/assets/${OemGuides.ASSET}").readText())!!

    @Test
    fun `every manufacturer family of RF-O1 has its guide`() {
        assertEquals("coloros", OemGuides.select(file, "OPPO", "OPPO").id)
        assertEquals("coloros", OemGuides.select(file, "OnePlus", null).id)
        assertEquals("oneui", OemGuides.select(file, "samsung", "samsung").id)
        assertEquals("hyperos", OemGuides.select(file, "Xiaomi", "Redmi").id)
        assertEquals("originos", OemGuides.select(file, "vivo", "iQOO").id)
        assertEquals("magicos", OemGuides.select(file, "HONOR", "HONOR").id)
        assertEquals("stock", OemGuides.select(file, "Google", "google").id)
        assertEquals("generic", OemGuides.select(file, "Unknown", null).id)
    }

    @Test
    fun `topics come in order and fall back to the generic guide`() {
        val topics = OemGuides.resolve(file, OemGuides.select(file, "OPPO", null))
        assertEquals(GuideTopics.all, topics.map { it.topic })
        assertTrue(topics.first().intents.size > 1)
        // Generic intents are appended as the last resort.
        val generic = OemGuides.genericOf(file).topics.getValue(GuideTopics.BATTERY).intents
        assertTrue(topics.first().intents.containsAll(generic))
    }

    @Test
    fun `every text key exists in both languages`() {
        val keys = file.guides.flatMap { g -> g.topics.values.map { it.textKey } }.toSet()
        listOf("values", "values-es").forEach { dir ->
            val xml = File("src/main/res/$dir/strings_oem.xml").readText()
            keys.forEach { assertTrue("$it missing in $dir", xml.contains("name=\"$it\"")) }
        }
    }

    @Test
    fun `broken files fall back`() {
        assertNull(OemGuides.parse("{"))
        assertNull(OemGuides.parse("""{"schema":9,"guides":[{"id":"x"}]}"""))
        assertNotNull(OemGuides.fallback().guides.single().topics[GuideTopics.BATTERY])
        assertEquals("package:com.x", IntentSpec(action = "a", data = "package:{package}").dataFor("com.x"))
        assertTrue(!IntentSpec().isUsable)
    }
}
