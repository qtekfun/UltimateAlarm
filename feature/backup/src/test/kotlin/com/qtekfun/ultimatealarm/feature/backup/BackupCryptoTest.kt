package com.qtekfun.ultimatealarm.feature.backup

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val params = KdfParams.FOR_TESTS
    private val plain = "nextcloud credentials".toByteArray()

    private fun failureOf(block: () -> Unit) = assertThrows(BackupException::class.java, block).failure

    @Test
    fun `round trip, also with a composed or decomposed password`() {
        val sealed = BackupCrypto.encrypt(plain, "contraseña".toCharArray(), params, SecureRandom())
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, "contraseña".toCharArray()))
        // "ñ" typed as n + combining tilde gives the same key (NFC normalisation).
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, "contraseña".toCharArray()))
    }

    @Test
    fun `wrong password and tampering are rejected`() {
        val sealed = BackupCrypto.encrypt(plain, "a".toCharArray(), params)
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, failureOf { BackupCrypto.decrypt(sealed, "b".toCharArray()) })
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, failureOf { BackupCrypto.decrypt(tampered, "a".toCharArray()) })
        // The header (including the KDF cost) is authenticated too.
        val costTampered = sealed.copyOf().also { it[13] = (it[13] + 1).toByte() }
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, failureOf { BackupCrypto.decrypt(costTampered, "a".toCharArray()) })
    }

    @Test
    fun `not a backup, truncated and future versions`() {
        assertEquals(BackupFailure.NOT_A_BACKUP, failureOf { BackupCrypto.decrypt("hello".toByteArray(), "a".toCharArray()) })
        val sealed = BackupCrypto.encrypt(plain, "a".toCharArray(), params)
        assertEquals(BackupFailure.CORRUPT, failureOf { BackupCrypto.decrypt(sealed.copyOf(10), "a".toCharArray()) })
        val future = sealed.copyOf().also { it[4] = 9 }
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, failureOf { BackupCrypto.decrypt(future, "a".toCharArray()) })
        val hugeCost = sealed.copyOf().also { it[6] = 0x7f }
        assertEquals(BackupFailure.CORRUPT, failureOf { BackupCrypto.decrypt(hugeCost, "a".toCharArray()) })
    }
}
