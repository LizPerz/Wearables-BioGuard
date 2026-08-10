package com.example.bioguard_wearos.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EncryptedRoomMigrationTest {
    @Test
    fun detectsOnlyPlaintextSqliteHeader() {
        val plaintext = File.createTempFile("bioguard-plain", ".db")
        val encrypted = File.createTempFile("bioguard-encrypted", ".db")
        try {
            plaintext.writeBytes("SQLite format 3\u0000payload".toByteArray(Charsets.US_ASCII))
            encrypted.writeBytes(ByteArray(32) { (it + 1).toByte() })

            assertTrue(EncryptedRoomMigration.hasPlaintextHeader(plaintext))
            assertFalse(EncryptedRoomMigration.hasPlaintextHeader(encrypted))
        } finally {
            plaintext.delete()
            encrypted.delete()
        }
    }
}
