package com.example.bioguard_wearos.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bioguard_prefs")

@Singleton
class BioGuardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val isFirstRunKey = booleanPreferencesKey("is_first_run")
    private val jwtTokenKey = stringPreferencesKey("jwt_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val tokenExpiryKey = longPreferencesKey("token_expiry")
    private val dispositivoIdKey = stringPreferencesKey("dispositivo_id")
    private val pacienteIdKey = stringPreferencesKey("paciente_id")

    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isFirstRunKey] ?: true
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[jwtTokenKey].isNullOrEmpty()
    }

    suspend fun setFirstRunComplete() {
        context.dataStore.edit { preferences ->
            preferences[isFirstRunKey] = false
        }
    }

    suspend fun saveAuthToken(token: String, refreshToken: String, expiracion: Long) {
        context.dataStore.edit { preferences ->
            preferences[jwtTokenKey] = token
            preferences[refreshTokenKey] = refreshToken
            preferences[tokenExpiryKey] = expiracion
        }
    }

    suspend fun getJwtToken(): String? {
        return context.dataStore.data.first()[jwtTokenKey]
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[refreshTokenKey]
    }

    suspend fun getTokenExpiry(): Long {
        return context.dataStore.data.first()[tokenExpiryKey] ?: 0L
    }

    suspend fun clearAuthToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(jwtTokenKey)
            preferences.remove(refreshTokenKey)
            preferences.remove(tokenExpiryKey)
        }
    }

    suspend fun saveDeviceBinding(dispositivoId: String, pacienteId: String) {
        context.dataStore.edit { preferences ->
            preferences[dispositivoIdKey] = dispositivoId
            preferences[pacienteIdKey] = pacienteId
        }
    }

    suspend fun getDispositivoId(): String? {
        return context.dataStore.data.first()[dispositivoIdKey]
    }

    suspend fun getPacienteId(): String? {
        return context.dataStore.data.first()[pacienteIdKey]
    }
}
