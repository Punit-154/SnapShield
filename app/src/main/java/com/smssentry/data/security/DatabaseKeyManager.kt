package com.smssentry.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher database encryption passphrase using Android Keystore.
 *
 * Flow:
 * 1. First launch: generates a random 32-byte passphrase, encrypts it with
 *    an AES key stored in Android Keystore, and saves the ciphertext to SharedPreferences.
 * 2. Subsequent launches: loads the encrypted passphrase from SharedPreferences
 *    and decrypts it using the Keystore key.
 *
 * The passphrase never appears in plaintext on disk — it's only in memory.
 */
object DatabaseKeyManager {

    private const val KEYSTORE_ALIAS = "smssentry_db_key"
    private const val PREFS_NAME = "smssentry_db_key_prefs"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "passphrase_iv"
    private const val GCM_TAG_LENGTH = 128

    /**
     * Get or create the database passphrase (as ByteArray for SQLCipher).
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(PREF_IV, null)

        return if (encryptedBase64 != null && ivBase64 != null) {
            // Decrypt existing passphrase
            val encrypted = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)
            decryptPassphrase(encrypted, iv)
        } else {
            // Generate new passphrase
            val passphrase = generateRandomPassphrase()
            val (encrypted, iv) = encryptPassphrase(passphrase)

            prefs.edit()
                .putString(PREF_ENCRYPTED_PASSPHRASE,
                    android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                .putString(PREF_IV,
                    android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply()

            passphrase
        }
    }

    private fun generateRandomPassphrase(): ByteArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let {
            return it as SecretKey
        }

        // Generate a new AES key in Android Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(passphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(passphrase)
        return Pair(encrypted, cipher.iv)
    }

    private fun decryptPassphrase(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }
}
