package com.example.bioguard_wearos.data.local

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DevSecOps hardening: clave maestra de la BD cifrada en repositorio.
 *
 * La base de datos Room (lecturas biométricas, riesgo, etc.) se cifra con
 * SQLCipher. La passphrase es aleatoria (256 bits) y, a su vez, se cifra con
 * AES/GCM mediante la clave del Android Keystore ([TokenCryptoManager]).
 * Solo se persiste la passphrase cifrada, nunca en claro.
 *
 * Si la clave del Keystore se pierde (reset de fábrica del device owner,
 * restauración de backups deshabilitada por diseño), la passphrase no se
 * puede recuperar y la BD se considera ilegible: se regenera una nueva.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: TokenCryptoManager
) {

    private companion object {
        const val KEY_FILE_NAME = "bioguard_db_passphrase.bin"
        const val PASS_BYTES = 32
    }

    private val keyFile: File = File(context.filesDir, KEY_FILE_NAME)

    fun getOrCreatePassphrase(): String {
        if (keyFile.exists()) {
            val encrypted = keyFile.readText()
            cryptoManager.decrypt(encrypted)?.let { return it }
        }
        return generateAndStore()
    }

    private fun generateAndStore(): String {
        val random = SecureRandom()
        val bytes = ByteArray(PASS_BYTES)
        random.nextBytes(bytes)
        val passphrase = Base64.encodeToString(bytes, Base64.NO_WRAP)
        keyFile.writeText(cryptoManager.encrypt(passphrase))
        return passphrase
    }
}
