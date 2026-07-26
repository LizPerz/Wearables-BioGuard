package com.example.bioguard_wearos.data.remote

import android.util.Log
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class AuthInterceptor(
    private val preferences: BioGuardPreferences
) : Interceptor {

    companion object {
        private const val TAG = "BIOGUARD_AUTH"
        private const val BASE_URL = "https://bioguard-api-lkvnq.ondigitalocean.app"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (path.contains("/api/Auth/")) {
            return chain.proceed(request)
        }

        val token = runBlocking { preferences.getJwtToken() }
        val authenticatedRequest = if (!token.isNullOrEmpty()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        val response = chain.proceed(authenticatedRequest)

        if (response.code == 401) {
            response.close()
            Log.w(TAG, "401 en $path, intentando refresh...")

            val refreshToken = runBlocking { preferences.getRefreshToken() }
            if (refreshToken.isNullOrEmpty()) {
                Log.w(TAG, "No hay refresh token, cerrando sesión")
                runBlocking { preferences.clearAuthToken() }
                return chain.proceed(request)
            }

            val refreshBody = """{"refreshToken":"$refreshToken"}"""
            val refreshRequest = Request.Builder()
                .url("$BASE_URL/api/Auth/refresh")
                .post(refreshBody.toRequestBody("application/json".toMediaType()))
                .build()

            val refreshResponse = chain.proceed(refreshRequest)

            if (refreshResponse.isSuccessful) {
                val body = refreshResponse.body?.string()
                refreshResponse.close()

                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        val newToken = json.getString("token")
                        val newRefreshToken = json.getString("refreshToken")
                        val expiracion = json.getLong("expiracion")

                        runBlocking {
                            preferences.saveAuthToken(newToken, newRefreshToken, expiracion)
                        }
                        Log.d(TAG, "Refresh OK, reintentando request")

                        val retryRequest = authenticatedRequest.newBuilder()
                            .removeHeader("Authorization")
                            .addHeader("Authorization", "Bearer $newToken")
                            .build()
                        return chain.proceed(retryRequest)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando refresh: ${e.message}")
                    }
                }
            } else {
                refreshResponse.close()
            }

            Log.w(TAG, "Refresh falló, cerrando sesión")
            runBlocking { preferences.clearAuthToken() }
        }

        return response
    }
}
