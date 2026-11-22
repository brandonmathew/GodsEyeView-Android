package com.godseyeview.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Provider keys for v2 (plan Q2): bring-your-own-key, NO backend. Every
 *  provider key the user enters lives here - AES-GCM encrypted with a
 *  key held in the Android Keystore; only ciphertext + IV are kept in
 *  private SharedPreferences. The app sets allowBackup=false, so nothing
 *  leaves the device through backup. Plaintext exists only in memory and
 *  at the moment of use. The Cesium ion token predates this class and
 *  stays in [TokenStore]; every other provider key is stored here. */
enum class ProviderKey(val prefName: String, val label: String) {
    GOOGLE("google", "Google Maps API key"),
    MAPILLARY("mapillary", "Mapillary client token"),
    AISSTREAM("aisstream", "AISStream API key"),
    FIRMS("firms", "NASA FIRMS map key"),
    TOMTOM("tomtom", "TomTom API key"),
    OPENAI("openai", "OpenAI API key"),
    GEMINI("gemini", "Gemini API key"),
}

class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun has(key: ProviderKey): Boolean = prefs.contains(cipherKey(key))

    fun save(key: ProviderKey, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(cipherKey(key), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ivKey(key), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(key: ProviderKey): String? {
        val ciphertextB64 = prefs.getString(cipherKey(key), null) ?: return null
        val ivB64 = prefs.getString(ivKey(key), null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. lockscreen reset) - the stored value is
            // unrecoverable; drop it rather than failing forever.
            clear(key)
            null
        }
    }

    fun clear(key: ProviderKey) {
        prefs.edit().remove(cipherKey(key)).remove(ivKey(key)).apply()
    }

    private fun cipherKey(key: ProviderKey) = "key_${key.prefName}_ciphertext"
    private fun ivKey(key: ProviderKey) = "key_${key.prefName}_iv"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "key_vault"
        private const val KEY_ALIAS = "gev2_key_vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
