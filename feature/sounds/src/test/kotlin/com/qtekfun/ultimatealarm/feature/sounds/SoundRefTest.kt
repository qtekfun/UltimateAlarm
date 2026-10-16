package com.qtekfun.ultimatealarm.feature.sounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SoundRefTest {
    @Test
    fun `round trip`() {
        listOf(SoundRef.Bundled("dawn_marimba"), SoundRef.System("content://media/internal/audio/media/12"), SoundRef.File("content://x/y.mp3"))
            .forEach { assertEquals(it, SoundRef.decode(it.encode())) }
    }

    @Test
    fun `unknown or empty text is the default`() {
        assertNull(SoundRef.decode(null))
        assertNull(SoundRef.decode(""))
        assertNull(SoundRef.decode("bundled:"))
        assertNull(SoundRef.decode("http://x"))
    }

    @Test
    fun `fallback chain`() {
        val file = SoundRef.File("content://a")
        assertEquals(listOf(file, SoundCatalog.DEFAULT_ALARM, SoundRef.System(SoundCatalog.SYSTEM_DEFAULT)), SoundCatalog.chain(file, backup = false))
        assertEquals(listOf(SoundCatalog.DEFAULT_BACKUP, SoundRef.System(SoundCatalog.SYSTEM_DEFAULT)), SoundCatalog.chain(null, backup = true))
        assertEquals(
            listOf(SoundCatalog.DEFAULT_ALARM, SoundRef.System(SoundCatalog.SYSTEM_DEFAULT)),
            SoundCatalog.chain(SoundCatalog.DEFAULT_ALARM, backup = false)
        )
    }

    @Test
    fun `catalog is consistent`() {
        assertEquals(13, SoundCatalog.all.size)
        assertEquals(SoundCatalog.all.size, SoundCatalog.all.map { it.id }.toSet().size)
        assertNotNull(SoundCatalog.byId(SoundCatalog.DEFAULT_ALARM.id))
        assertNotNull(SoundCatalog.byId(SoundCatalog.DEFAULT_BACKUP.id))
        assertEquals(9, SoundCatalog.alarmChoices().size)
        assertEquals(2, SoundCatalog.backupChoices().size)
    }

    @Test
    fun `every catalog entry has its file`() {
        val dir = java.io.File("src/main/assets/sounds")
        SoundCatalog.all.forEach { assert(java.io.File(dir, "${it.id}.ogg").isFile) { "missing ${it.assetPath}" } }
    }
}
