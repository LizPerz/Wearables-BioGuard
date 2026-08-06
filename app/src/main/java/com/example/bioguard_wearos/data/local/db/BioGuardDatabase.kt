package com.example.bioguard_wearos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.bioguard_wearos.data.local.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

        /**
         * DevSecOps hardening: la BD se abre cifrada con SQLCipher.
         * La passphrase la gestiona [DatabaseKeyManager] (clave del Android
         * Keystore, nunca en claro en disco).
         */
        fun create(context: Context, keyManager: DatabaseKeyManager): BioGuardDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(
                keyManager.getOrCreatePassphrase().toByteArray(Charsets.UTF_8)
            )
            return Room.databaseBuilder(
                context.applicationContext,
                BioGuardDatabase::class.java,
                DATABASE_NAME
            ).openHelperFactory(factory).build()
        }
    }
}
