package com.example.bioguard_wearos.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DevSecOps hardening: cifrado de credenciales en repositorio.
 *
 * Los tokens JWT (access + refresh) se cifran con AES/GCM antes de
 * persistirse en DataStore. La clave maestra se genera y protege en el
 * Android Keystore del dispositivo, fuera del alcance del filesystem
 * normal de la app.
 */
@Singleton
class TokenCryptoManager @Inject constructor() {

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "bioguard_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /**
     * Devuelve null si no puede descifrarse (p.ej. clave eliminada tras
     * un restablecimiento o dato corrupto). El llamador debe tratarlo
     * como sesión inexistente.
     */
    fun decrypt(encryptedText: String): String? {
        return try {
            val payload = Base64.decode(encryptedText, Base64.NO_WRAP)
            require(payload.size > IV_SIZE_BYTES)
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
