package com.example.bioguard_wearos.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bioguard_wearos.domain.risk.RiskThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEYSTORE_ALIAS = "bioguard_wear_auth_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE_BYTES = 12
private const val TAG_LENGTH_BITS = 128

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bioguard_prefs")

@Singleton
class BioGuardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val isFirstRunKey = booleanPreferencesKey("is_first_run")
    private val dispositivoIdKey = stringPreferencesKey("dispositivo_id")
    private val pacienteIdKey = stringPreferencesKey("paciente_id")

    private val riskCriticalBpmHighKey = floatPreferencesKey("risk_critical_bpm_high")
    private val riskCriticalBpmLowKey = floatPreferencesKey("risk_critical_bpm_low")
    private val riskCriticalTempKey = floatPreferencesKey("risk_critical_temp")
    private val riskModerateBpmKey = floatPreferencesKey("risk_moderate_bpm")
    private val riskModerateTempKey = floatPreferencesKey("risk_moderate_temp")
    private val riskModerateGsrKey = floatPreferencesKey("risk_moderate_gsr")

    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isFirstRunKey] ?: true
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val id = preferences[dispositivoIdKey]
        !id.isNullOrEmpty() && !decrypt(id).isNullOrEmpty()
    }

    suspend fun setFirstRunComplete() {
        context.dataStore.edit { preferences ->
            preferences[isFirstRunKey] = false
        }
    }

    suspend fun clearAuthToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(dispositivoIdKey)
            preferences.remove(pacienteIdKey)
        }
    }

    suspend fun saveDeviceBinding(dispositivoId: String, pacienteId: String) {
        context.dataStore.edit { preferences ->
            preferences[dispositivoIdKey] = encrypt(dispositivoId)
            preferences[pacienteIdKey] = encrypt(pacienteId)
        }
    }

    suspend fun getDispositivoId(): String? {
        return context.dataStore.data.first()[dispositivoIdKey]?.let { decrypt(it) }
    }

    suspend fun getPacienteId(): String? {
        return context.dataStore.data.first()[pacienteIdKey]?.let { decrypt(it) }
    }

    val riskThresholds: Flow<RiskThresholds> = context.dataStore.data.map { prefs ->
        RiskThresholds(
            criticalBpmHigh = prefs[riskCriticalBpmHighKey] ?: RiskThresholds.DEFAULT.criticalBpmHigh,
            criticalBpmLow = prefs[riskCriticalBpmLowKey] ?: RiskThresholds.DEFAULT.criticalBpmLow,
            criticalTemp = prefs[riskCriticalTempKey] ?: RiskThresholds.DEFAULT.criticalTemp,
            moderateBpm = prefs[riskModerateBpmKey] ?: RiskThresholds.DEFAULT.moderateBpm,
            moderateTemp = prefs[riskModerateTempKey] ?: RiskThresholds.DEFAULT.moderateTemp,
            moderateGsr = prefs[riskModerateGsrKey] ?: RiskThresholds.DEFAULT.moderateGsr
        )
    }

    suspend fun saveRiskThresholds(thresholds: RiskThresholds) {
        context.dataStore.edit { prefs ->
            prefs[riskCriticalBpmHighKey] = thresholds.criticalBpmHigh
            prefs[riskCriticalBpmLowKey] = thresholds.criticalBpmLow
            prefs[riskCriticalTempKey] = thresholds.criticalTemp
            prefs[riskModerateBpmKey] = thresholds.moderateBpm
            prefs[riskModerateTempKey] = thresholds.moderateTemp
            prefs[riskModerateGsrKey] = thresholds.moderateGsr
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(cipherData: String): String? {
        return try {
            val data = Base64.decode(cipherData, Base64.NO_WRAP)
            if (data.size <= IV_SIZE_BYTES) return null
            val iv = data.copyOfRange(0, IV_SIZE_BYTES)
            val cipherText = data.copyOfRange(IV_SIZE_BYTES, data.size)
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
