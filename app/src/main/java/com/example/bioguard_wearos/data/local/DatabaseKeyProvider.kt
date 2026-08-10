package com.example.bioguard_wearos.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyProvider {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bioguard_wear_database_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "bioguard_database_security"
    private const val PASSPHRASE_KEY = "encrypted_passphrase"
    private const val IV_SIZE = 12

    @SuppressLint("ApplySharedPref")
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = preferences.getString(PASSPHRASE_KEY, null)
        if (encrypted != null) return decrypt(encrypted)

        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        preferences.edit().putString(PASSPHRASE_KEY, encrypt(passphrase)).commit()
        return passphrase
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): ByteArray {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Invalid encrypted database passphrase" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, IV_SIZE)))
        }
        return cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size))
    }
}
