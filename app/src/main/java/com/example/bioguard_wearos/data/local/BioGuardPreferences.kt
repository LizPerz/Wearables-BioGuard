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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bioguard_wearos.domain.risk.RiskThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
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
    private val installIdKey = stringPreferencesKey("install_id")
    private val trustedMobileNodeIdKey = stringPreferencesKey("trusted_mobile_node_id")
    private val pairingNonceKey = stringPreferencesKey("pairing_nonce")
    private val pairingIssuedAtKey = longPreferencesKey("pairing_issued_at")
    private val liveTelemetryIntervalSecondsKey = intPreferencesKey("live_telemetry_interval_seconds")

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
            preferences.remove(trustedMobileNodeIdKey)
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

    suspend fun getOrCreateInstallId(): String {
        var installId = ""
        context.dataStore.edit { preferences ->
            installId = preferences[installIdKey] ?: UUID.randomUUID().toString().also {
                preferences[installIdKey] = it
            }
        }
        return installId
    }

    suspend fun saveTrustedMobileNodeId(nodeId: String) {
        require(nodeId.isNotBlank()) { "Trusted mobile node ID cannot be blank" }
        context.dataStore.edit { it[trustedMobileNodeIdKey] = encrypt(nodeId) }
    }

    suspend fun getTrustedMobileNodeId(): String? =
        context.dataStore.data.first()[trustedMobileNodeIdKey]?.let { decrypt(it) }

    suspend fun clearTrustedMobileNodeId() {
        context.dataStore.edit { preferences ->
            preferences.remove(trustedMobileNodeIdKey)
            preferences.remove(pairingNonceKey)
            preferences.remove(pairingIssuedAtKey)
        }
    }

    suspend fun getLiveTelemetryIntervalSeconds(): Int =
        context.dataStore.data.first()[liveTelemetryIntervalSecondsKey] ?: DEFAULT_LIVE_TELEMETRY_INTERVAL_SECONDS

    suspend fun saveLiveTelemetryIntervalSeconds(intervalSeconds: Int) {
        require(intervalSeconds in MIN_LIVE_TELEMETRY_INTERVAL_SECONDS..MAX_LIVE_TELEMETRY_INTERVAL_SECONDS) {
            "Intervalo de telemetria fuera de rango"
        }
        context.dataStore.edit { it[liveTelemetryIntervalSecondsKey] = intervalSeconds }
    }

    suspend fun savePairingChallenge(nonce: String, issuedAtSeconds: Long) {
        require(nonce.length >= 16) { "Pairing nonce is too short" }
        context.dataStore.edit { preferences ->
            preferences[pairingNonceKey] = encrypt(nonce)
            preferences[pairingIssuedAtKey] = issuedAtSeconds
        }
    }

    suspend fun consumePairingChallenge(
        nonce: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        var accepted = false
        context.dataStore.edit { preferences ->
            val encryptedStored = preferences[pairingNonceKey]
            val expected = encryptedStored?.let { decrypt(it) }
            val issuedAt = preferences[pairingIssuedAtKey] ?: 0L
            accepted = !expected.isNullOrBlank() &&
                issuedAt in (nowSeconds - PAIRING_MAX_AGE_SECONDS)..(nowSeconds + 30L) &&
                MessageDigest.isEqual(
                    expected.toByteArray(Charsets.UTF_8),
                    nonce.toByteArray(Charsets.UTF_8)
                )
            if (accepted || issuedAt < nowSeconds - PAIRING_MAX_AGE_SECONDS) {
                preferences.remove(pairingNonceKey)
                preferences.remove(pairingIssuedAtKey)
            }
        }
        return accepted
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

    private companion object {
        const val PAIRING_MAX_AGE_SECONDS = 300L
        const val DEFAULT_LIVE_TELEMETRY_INTERVAL_SECONDS = 30
        const val MIN_LIVE_TELEMETRY_INTERVAL_SECONDS = 15
        const val MAX_LIVE_TELEMETRY_INTERVAL_SECONDS = 3_600
    }
}
