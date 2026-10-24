package com.dharmachakra.fba_android.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted store for the v2 OAuth tokens. The access and refresh tokens are
 * long-lived secrets, so they're encrypted with an AES-GCM key held in the
 * AndroidKeyStore (hardware-backed where available) rather than written as plain
 * text, and never leave the device.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("v2_auth", Context.MODE_PRIVATE)

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        prefs.edit()
            .putString(KEY_ACCESS, encrypt(accessToken))
            .putString(KEY_REFRESH, encrypt(refreshToken))
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)?.let(::decrypt)
    fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)?.let(::decrypt)
    val isLoggedIn: Boolean get() = prefs.contains(KEY_REFRESH)

    fun clear() = prefs.edit().clear().apply()

    // --- AES-GCM via AndroidKeyStore ---------------------------------------

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(ct)
    }

    private fun decrypt(stored: String): String? = try {
        val (ivB64, ctB64) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, unb64(ivB64)))
        String(cipher.doFinal(unb64(ctB64)), Charsets.UTF_8)
    } catch (_: Exception) {
        null   // key rotated / data corrupt → treat as logged out
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fba_v2_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
