package com.mimora.app.feature.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecurityManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_APP = "mimora_app_master_key"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KDF_ITERATIONS = 100_000
        private const val KDF_KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
        private const val BACKUP_HEADER = "MIMORA_ENC_V1"

        // Word list for 24-word recovery phrase (BIP-39 subset sample / algorithmically verified)
        val WORD_LIST = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
            "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
            "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
            "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
            "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
            "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
            "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
            "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
            "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor",
            "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact",
            "artist", "artwork", "ask", "aspect", "assault", "asset", "assist", "assume",
            "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
            "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado",
            "avoid", "awake", "aware", "away", "awesome", "awful", "awkward", "axis",
            "baby", "bachelor", "bacon", "badge", "bag", "balance", "balcony", "ball",
            "bamboo", "banana", "banner", "bar", "barely", "bargain", "barrel", "base",
            "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become",
            "beef", "before", "begin", "behave", "behind", "believe", "below", "belt",
            "bench", "benefit", "best", "betray", "better", "between", "beyond", "bicycle",
            "bid", "bike", "bind", "biology", "bird", "birth", "bitter", "black",
            "blade", "blame", "blanket", "blast", "bleak", "bless", "blind", "blood",
            "blossom", "blouse", "blue", "blur", "blush", "board", "boat", "body",
            "boil", "bomb", "bone", "bonus", "book", "boost", "border", "boring",
            "borrow", "boss", "bottom", "bounce", "box", "boy", "bracket", "brain",
            "brand", "brass", "brave", "bread", "breeze", "brick", "bridge", "brief",
            "bright", "bring", "brisk", "broccoli", "broken", "bronze", "broom", "brother",
            "brown", "brush", "bubble", "buddy", "budget", "buffalo", "build", "bulb",
            "bulk", "bullet", "bundle", "bunker", "burden", "burger", "burst", "bus",
            "business", "busy", "butter", "buyer", "buzz", "cabbage", "cabin", "cable",
            "cactus", "cage", "cake", "call", "calm", "camera", "camp", "can",
            "canal", "cancel", "candy", "cannon", "canoe", "canvas", "canyon", "capable"
        )
    }

    private val secureRandom = SecureRandom()

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS_APP)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS_APP,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // AndroidKeyStore is not present in JVM local unit tests environment
        }
    }

    private fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS_APP, null) as SecretKey
    }

    // --- LOCAL SECRET AT REST (Android Keystore-backed) ---

    /**
     * Encrypts a secret with the non-exportable Android Keystore key so it can
     * be persisted in DataStore without ever appearing in plaintext on disk.
     * Format: Base64( IV[12] || CIPHERTEXT+TAG ).
     */
    fun encryptLocalSecret(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // The Keystore key has randomized encryption required (the default),
        // so it generates the IV itself and rejects a caller-provided one.
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Reverses [encryptLocalSecret]. GCM authentication rejects any tampering. */
    fun decryptLocalSecret(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted secret" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // --- PIN & APP LOCK ---

    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val spec = PBEKeySpec(pin.toCharArray(), salt, KDF_ITERATIONS, KDF_KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        val combined = ByteArray(SALT_LENGTH_BYTES + hash.size)
        System.arraycopy(salt, 0, combined, 0, SALT_LENGTH_BYTES)
        System.arraycopy(hash, 0, combined, SALT_LENGTH_BYTES, hash.size)

        return bytesToHex(combined)
    }

    fun verifyPin(pin: String, storedHashHex: String): Boolean {
        return try {
            val combined = hexToBytes(storedHashHex)
            if (combined.size <= SALT_LENGTH_BYTES) return false

            val salt = ByteArray(SALT_LENGTH_BYTES)
            val storedHash = ByteArray(combined.size - SALT_LENGTH_BYTES)
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH_BYTES)
            System.arraycopy(combined, SALT_LENGTH_BYTES, storedHash, 0, storedHash.size)

            val spec = PBEKeySpec(pin.toCharArray(), salt, KDF_ITERATIONS, KDF_KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val computedHash = factory.generateSecret(spec).encoded

            Arrays.equals(storedHash, computedHash)
        } catch (e: Exception) {
            false
        }
    }

    // --- RECOVERY PHRASE GENERATION (24 WORDS) ---

    /**
     * Generates a brand-new vault key for THIS installation.
     *
     * There is no default, shared or hardcoded phrase: every call draws from the
     * OS entropy pool via [secureRandom]. Words are sampled without replacement
     * (Fisher-Yates) so each install receives its own distinct 24-word key.
     */
    fun generate24WordRecoveryPhrase(): List<String> {
        val shuffled = WORD_LIST.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        return shuffled.take(24)
    }

    fun validateRecoveryPhrase(phraseWords: List<String>): Boolean {
        if (phraseWords.size != 24) return false
        return phraseWords.all { WORD_LIST.contains(it.lowercase().trim()) }
    }

    // --- KEY DERIVATION FROM RECOVERY PHRASE ---

    private fun deriveKeyFromPhrase(phraseWords: List<String>, salt: ByteArray): SecretKey {
        val phrase = phraseWords.joinToString(" ") { it.lowercase().trim() }
        val spec = PBEKeySpec(phrase.toCharArray(), salt, KDF_ITERATIONS, KDF_KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val rawKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(rawKey, "AES")
    }

    // --- AUTHENTICATED ENCRYPTION (AES-256-GCM) ---

    /**
     * Encrypts plaintext payload using AES-256-GCM with a key derived from 24-word recovery phrase.
     * Output format: [HEADER: 13 bytes][SALT: 16 bytes][IV: 12 bytes][CIPHERTEXT + TAG: N bytes]
     */
    fun encryptWithRecoveryPhrase(plaintext: ByteArray, phraseWords: List<String>): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val secretKey = deriveKeyFromPhrase(phraseWords, salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val headerBytes = BACKUP_HEADER.toByteArray(Charsets.UTF_8)
        cipher.updateAAD(headerBytes) // Authenticate header

        val ciphertext = cipher.doFinal(plaintext)

        val buffer = ByteBuffer.allocate(headerBytes.size + salt.size + iv.size + ciphertext.size)
        buffer.put(headerBytes)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)

        return buffer.array()
    }

    /**
     * Decrypts encrypted backup payload using 24-word recovery phrase and verifies integrity.
     */
    fun decryptWithRecoveryPhrase(encryptedData: ByteArray, phraseWords: List<String>): ByteArray {
        val headerBytes = BACKUP_HEADER.toByteArray(Charsets.UTF_8)
        if (encryptedData.size < headerBytes.size + SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted backup: payload too short.")
        }

        val buffer = ByteBuffer.wrap(encryptedData)
        val readHeader = ByteArray(headerBytes.size)
        buffer.get(readHeader)

        if (!Arrays.equals(headerBytes, readHeader)) {
            throw IllegalArgumentException("Invalid backup format or unsupported encryption version.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        buffer.get(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val secretKey = deriveKeyFromPhrase(phraseWords, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        cipher.updateAAD(headerBytes)

        return cipher.doFinal(ciphertext)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
