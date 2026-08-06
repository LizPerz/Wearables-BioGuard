package com.example.bioguard_wearos.data.remote

import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.data.remote.api.BioGuardApi
import com.example.bioguard_wearos.data.remote.api.BioGuardApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json, preferences: BioGuardPreferences): HttpClient {
        val authInterceptor = AuthInterceptor(preferences)

        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }

            engine {
                config {
                    addInterceptor(authInterceptor)
                    // DevSecOps hardening: certificado pinning del backend.
                    certificatePinner(TlsSecurity.certificatePinner)
                    connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideBioGuardApi(client: HttpClient): BioGuardApi {
        return BioGuardApiImpl(client)
    }
}
