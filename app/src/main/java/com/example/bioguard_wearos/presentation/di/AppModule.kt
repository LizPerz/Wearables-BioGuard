package com.example.bioguard_wearos.presentation.di

import android.content.Context
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.data.local.BiometricReadingRepositoryImpl
import com.example.bioguard_wearos.data.local.DatabaseKeyManager
import com.example.bioguard_wearos.data.local.db.BiometricReadingDao
import com.example.bioguard_wearos.data.local.db.BioGuardDatabase
import com.example.bioguard_wearos.data.remote.api.BioGuardApi
import com.example.bioguard_wearos.data.repository.SensorDataRepositoryImpl
import com.example.bioguard_wearos.data.repository.SyncRepositoryImpl
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBioGuardDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): BioGuardDatabase {
        return BioGuardDatabase.create(context, keyManager)
    }

    @Provides
    @Singleton
    fun provideBiometricReadingDao(
        database: BioGuardDatabase
    ): BiometricReadingDao {
        return database.biometricReadingDao()
    }

    @Provides
    @Singleton
    fun provideBiometricReadingRepository(
        dao: BiometricReadingDao
    ): BiometricReadingRepository {
        return BiometricReadingRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideSensorDataRepository(
        @ApplicationContext context: Context,
        readingRepository: BiometricReadingRepository
    ): SensorDataRepository {
        return SensorDataRepositoryImpl(context, readingRepository)
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context,
        readingRepository: BiometricReadingRepository,
        preferences: BioGuardPreferences
    ): SyncRepository {
        return SyncRepositoryImpl(context, readingRepository, preferences)
    }
}
