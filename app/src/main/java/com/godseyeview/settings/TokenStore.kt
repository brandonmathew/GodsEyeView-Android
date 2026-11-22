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

/** Cesium ion token storage (plan Q14/Q35). The token is encrypted with
 *  an AES-GCM key held in the Android Keystore; only ciphertext + IV are
 *  kept in private SharedPreferences. The app sets allowBackup=false, so
 *  neither the ciphertext nor anything else leaves the device through
 *  backup. The plaintext token exists only in memory and in the WebView
 *  page (where Cesium needs it). */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasToken(): Boolean = prefs.contains(KEY_CIPHERTEXT)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadToken(): String? {
        val ciphertextB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. lockscreen reset) - the stored token is
            // unrecoverable; drop it rather than failing forever.
            clearToken()
            null
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

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
        private const val PREFS = "token_store"
        private const val KEY_ALIAS = "gev_ion_token"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val KEY_IV = "token_iv"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
