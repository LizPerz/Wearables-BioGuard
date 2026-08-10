package com.example.bioguard_wearos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bioguard_wearos.data.local.db.BioGuardDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BioGuardMigrationTest {
    private val dbName = "wearable-migration-test.db"

    @Test
    fun `migration 2 to 3 preserves readings and adds durable metrics`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(dbName)
        context.openOrCreateDatabase(dbName, android.content.Context.MODE_PRIVATE, null).apply {
            execSQL(
                "CREATE TABLE biometric_readings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, " +
                    "bpm REAL NOT NULL, temperature REAL NOT NULL, gsr REAL NOT NULL, " +
                    "bmi REAL NOT NULL, riskLevel TEXT NOT NULL, synced INTEGER NOT NULL)"
            )
            execSQL(
                "CREATE TABLE outbound_messages (" +
                    "id TEXT NOT NULL PRIMARY KEY, path TEXT NOT NULL, payloadJson TEXT NOT NULL, " +
                    "priority INTEGER NOT NULL, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL)"
            )
            execSQL(
                "INSERT INTO biometric_readings " +
                    "(timestamp,bpm,temperature,gsr,bmi,riskLevel,synced) " +
                    "VALUES (1000,72.0,36.5,2.0,24.0,'OPTIMAL',0)"
            )
            execSQL("PRAGMA user_version = 2")
            close()
        }

        val migrated = Room.databaseBuilder(context, BioGuardDatabase::class.java, dbName)
            .addMigrations(BioGuardDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        migrated.openHelper.writableDatabase
        migrated.query("SELECT bpm, hrvRmssd, hrvSdnn, stressEstimate, steps FROM biometric_readings", null).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(72.0, cursor.getDouble(0), 0.001)
            assertEquals(0.0, cursor.getDouble(1), 0.001)
            assertEquals(0.0, cursor.getDouble(2), 0.001)
            assertEquals(0.0, cursor.getDouble(3), 0.001)
            check(cursor.isNull(4))
        }
        migrated.close()
    }
}
