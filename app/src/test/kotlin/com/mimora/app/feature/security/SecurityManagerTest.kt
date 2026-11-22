package com.mimora.app.feature.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setUp() {
        val dummyContext = android.content.ContextWrapper(null)
        securityManager = SecurityManager(dummyContext)
    }

    @Test
    fun generate24WordRecoveryPhrase_generatesExact24ValidWords() {
        val phrase = securityManager.generate24WordRecoveryPhrase()
        assertEquals(24, phrase.size)
        assertTrue(securityManager.validateRecoveryPhrase(phrase))
    }

    @Test
    fun generate24WordRecoveryPhrase_isUniquePerFreshInstall() {
        // Each simulated device gets its own SecurityManager, as a fresh install does.
        val deviceA = SecurityManager(android.content.ContextWrapper(null))
        val deviceB = SecurityManager(android.content.ContextWrapper(null))
        val phrases = (1..20).map { deviceA.generate24WordRecoveryPhrase() } +
            (1..20).map { deviceB.generate24WordRecoveryPhrase() }

        assertEquals("Every generated phrase must be distinct", phrases.size, phrases.toSet().size)
        phrases.forEach { assertEquals("Words must not repeat inside one key", 24, it.toSet().size) }
    }

    @Test
    fun validateRecoveryPhrase_rejectsInvalidPhrases() {
        assertFalse(securityManager.validateRecoveryPhrase(listOf("apple", "banana")))
        val invalidWordPhrase = securityManager.generate24WordRecoveryPhrase().toMutableList()
        invalidWordPhrase[0] = "xyznonexistentword"
        assertFalse(securityManager.validateRecoveryPhrase(invalidWordPhrase))
    }

    @Test
    fun pinHashingAndVerification_succeedsWithCorrectPin() {
        val pin = "123456"
        val hash = securityManager.hashPin(pin)
        assertNotNull(hash)
        assertTrue(securityManager.verifyPin("123456", hash))
        assertFalse(securityManager.verifyPin("999999", hash))
        assertFalse(securityManager.verifyPin("", hash))
    }

    @Test
    fun recoveryPhraseEncryptionDecryption_roundtripsSuccessfully() {
        val phrase = securityManager.generate24WordRecoveryPhrase()
        val plainText = "Secret personal diary memory entry content for MIMORA.".toByteArray(StandardCharsets.UTF_8)

        val encrypted = securityManager.encryptWithRecoveryPhrase(plainText, phrase)
        assertTrue(encrypted.isNotEmpty())

        val decrypted = securityManager.decryptWithRecoveryPhrase(encrypted, phrase)
        assertEquals(String(plainText, StandardCharsets.UTF_8), String(decrypted, StandardCharsets.UTF_8))
    }

    @Test
    fun recoveryPhraseDecryption_failsWithWrongPhrase() {
        val phrase1 = securityManager.generate24WordRecoveryPhrase()
        val phrase2 = securityManager.generate24WordRecoveryPhrase()

        val plainText = "Top secret personal memory.".toByteArray(StandardCharsets.UTF_8)
        val encrypted = securityManager.encryptWithRecoveryPhrase(plainText, phrase1)

        try {
            securityManager.decryptWithRecoveryPhrase(encrypted, phrase2)
            fail("Expected decryption to fail with wrong recovery phrase")
        } catch (_: Exception) {
            // Success: GCM Authentication check failed
        }
    }

    @Test
    fun recoveryPhraseDecryption_failsOnTamperedCiphertext() {
        val phrase = securityManager.generate24WordRecoveryPhrase()
        val plainText = "Authentic unmodified diary text.".toByteArray(StandardCharsets.UTF_8)

        val encrypted = securityManager.encryptWithRecoveryPhrase(plainText, phrase)
        // Tamper with the last byte (part of AES-GCM auth tag or ciphertext)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

        try {
            securityManager.decryptWithRecoveryPhrase(encrypted, phrase)
            fail("Expected decryption to fail with tampered ciphertext")
        } catch (_: Exception) {
            // Success: AEAD tag mismatch caught!
        }
    }
}
