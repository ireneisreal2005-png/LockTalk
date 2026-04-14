package com.proj.locktalk

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    // This is the shared secret key — 32 chars = 256 bit AES
    // In a real production app this would be exchanged securely
    // For your project this is still real AES-256 encryption
    private const val SECRET_KEY = "LockTalk2024SecureKey!@#XYZ12345"

    private fun getKey(): SecretKeySpec {
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            // combine iv + encrypted and base64 encode
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // fallback to plain if error
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            val iv = combined.sliceArray(0..15)
            val encrypted = combined.sliceArray(16 until combined.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedText // fallback to show as-is if error
        }
    }
}