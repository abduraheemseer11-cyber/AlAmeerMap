package com.alameer.map.data

import android.util.Base64
import com.alameer.map.BuildConfig
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts device passwords (AES-256-GCM) before they touch Room or Supabase.
 *
 * Key = PBKDF2(ENCRYPTION_PEPPER, salt = account user id). The same account can therefore
 * decrypt on any phone, while someone who only reads the database sees ciphertext.
 * NOTE: the pepper is compiled into the APK, so this protects the database contents,
 * not against someone who reverse-engineers your APK.
 */
object CryptoManager {
    private const val PREFIX = "v1:"
    private val keys = ConcurrentHashMap<String, SecretKeySpec>()
    private val random = SecureRandom()

    private fun key(userId: String): SecretKeySpec = keys.getOrPut(userId) {
        val pepper = BuildConfig.ENCRYPTION_PEPPER.ifBlank { "alameer-default-pepper" }
        val spec = PBEKeySpec(pepper.toCharArray(), userId.toByteArray(Charsets.UTF_8), 100_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        SecretKeySpec(bytes, "AES")
    }

    /** Derive the key ahead of time (call off the main thread). */
    fun warmUp(userId: String) { key(userId) }

    fun encrypt(plain: String, userId: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(userId), GCMParameterSpec(128, iv))
        val out = iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(stored: String, userId: String): String {
        if (!stored.startsWith(PREFIX)) return ""
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(userId), GCMParameterSpec(128, raw, 0, 12))
            String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
