package com.example.bioguard_wearos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bioguard_wearos.data.local.DatabaseKeyProvider
import com.example.bioguard_wearos.data.local.EncryptedRoomMigration
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [BiometricReadingEntity::class, OutboundMessageEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(RiskLevelConverter::class)
abstract class BioGuardDatabase : RoomDatabase() {

    abstract fun biometricReadingDao(): BiometricReadingDao
    abstract fun outboundMessageDao(): OutboundMessageDao

    companion object {
        const val DATABASE_NAME = "bioguard_db"
        const val MAX_READINGS = 8640
        const val RETENTION_DAYS = 1L

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbound_messages` (" +
                        "`id` TEXT NOT NULL, `path` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "`source_message_id` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `biometric_readings` ADD COLUMN `hrvRmssd` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `biometric_readings` ADD COLUMN `hrvSdnn` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `biometric_readings` ADD COLUMN `stressEstimate` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `biometric_readings` ADD COLUMN `steps` INTEGER")
                val hasSourceMessageId = db.query("PRAGMA table_info(outbound_messages)").use { cursor ->
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == "source_message_id") {
                            found = true
                            break
                        }
                    }
                    found
                }
                if (!hasSourceMessageId) {
                    db.execSQL("ALTER TABLE `outbound_messages` ADD COLUMN `source_message_id` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        fun create(context: Context): BioGuardDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
            EncryptedRoomMigration.migratePlaintextIfNeeded(context, DATABASE_NAME, passphrase)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                BioGuardDatabase::class.java,
                DATABASE_NAME
            ).openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
