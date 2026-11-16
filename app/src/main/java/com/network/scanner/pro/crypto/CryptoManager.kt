package com.network.scanner.pro.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All secret handling of the app.
 *
 *  1. A Keystore-backed [MasterKey] protects [EncryptedSharedPreferences].
 *  2. Inside those prefs we keep three random values, generated on first use:
 *       - the SQLCipher database passphrase,
 *       - a 256-bit master secret and a 128-bit salt for field encryption.
 *  3. The AES-256 field key is derived with PBKDF2-HMAC-SHA256 (120k iterations)
 *     from master secret + salt, then cached in memory ([warmUp] pre-computes it).
 *  4. [encrypt] / [decrypt] use AES-256-GCM with a fresh random 96-bit IV per
 *     message. Token format: "v1:" + Base64(iv || ciphertext || tag).
 *
 * Note: the key never leaves this device, so a password encrypted here can only
 * be decrypted here (also after cloud sync).
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val random = SecureRandom()
    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    private val prefs: SharedPreferences by lazy { openSecurePrefs() }

    /** Derives the field key and creates the DB passphrase ahead of time (call off the main thread). */
    fun warmUp() {
        getKey()
        getDatabasePassphrase()
    }

    /** 32 random bytes, created once and stored in encrypted prefs. Used as the SQLCipher key. */
    fun getDatabasePassphrase(): ByteArray = getOrCreateBytes(KEY_DB_PASSPHRASE, 32)

    /** Encrypts [plainText]; returns "" for empty input. */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /** Decrypts a token produced by [encrypt]; returns null when it is invalid or the key differs. */
    fun decrypt(token: String): String? {
        if (token.isEmpty()) return ""
        if (!token.startsWith(PREFIX)) return null
        return try {
            val data = Base64.decode(token.removePrefix(PREFIX), Base64.NO_WRAP)
            if (data.size <= IV_BYTES) return null
            val iv = data.copyOfRange(0, IV_BYTES)
            val body = data.copyOfRange(IV_BYTES, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private fun getKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(lock) {
            cachedKey ?: deriveKey().also { cachedKey = it }
        }
    }

    private fun deriveKey(): SecretKey {
        val secret = getOrCreateBytes(KEY_MASTER_SECRET, 32)
        val salt = getOrCreateBytes(KEY_SALT, 16)
        val password = Base64.encodeToString(secret, Base64.NO_WRAP).toCharArray()
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun getOrCreateBytes(name: String, size: Int): ByteArray = synchronized(lock) {
        val existing = prefs.getString(name, null)
        if (existing != null) {
            Base64.decode(existing, Base64.NO_WRAP)
        } else {
            val bytes = ByteArray(size).also { random.nextBytes(it) }
            // commit() (not apply()) so the value is durable before it is used.
            prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
            bytes
        }
    }

    private fun openSecurePrefs(): SharedPreferences {
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            create()
        } catch (e: Exception) {
            // The Keystore key was invalidated (e.g. lock-screen change/restore).
            // Old secrets are unrecoverable: reset them and the unreadable database.
            context.deleteSharedPreferences(PREFS_NAME)
            context.deleteDatabase(DATABASE_FILE)
            create()
        }
    }

    companion object {
        const val DATABASE_FILE = "netguard_pro.db"

        private const val PREFS_NAME = "netguard_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_MASTER_SECRET = "field_master_secret"
        private const val KEY_SALT = "field_salt"
        private const val PREFIX = "v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
    }
}
