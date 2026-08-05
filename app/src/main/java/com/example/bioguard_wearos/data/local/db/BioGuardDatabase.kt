package com.example.bioguard_wearos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BiometricReadingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RiskLevelConverter::class)
abstract class BioGuardDatabase : RoomDatabase() {

    abstract fun biometricReadingDao(): BiometricReadingDao

    companion object {
        const val DATABASE_NAME = "bioguard_db"
        const val MAX_READINGS = 8640
        const val RETENTION_DAYS = 1L

        fun create(context: Context): BioGuardDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BioGuardDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
