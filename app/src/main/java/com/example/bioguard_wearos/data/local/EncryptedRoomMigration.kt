package com.example.bioguard_wearos.data.local

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

internal object EncryptedRoomMigration {
    private val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun migratePlaintextIfNeeded(context: Context, databaseName: String, passphrase: ByteArray) {
        val source = context.getDatabasePath(databaseName)
        if (!source.exists() || !hasPlaintextHeader(source)) return

        source.parentFile?.mkdirs()
        val encrypted = File(source.parentFile, "$databaseName.encrypted-migration")
        val backup = File(source.parentFile, "$databaseName.plaintext-backup")
        deleteDatabaseFiles(encrypted)
        deleteDatabaseFiles(backup)

        exportEncryptedCopy(source, encrypted, passphrase)
        verifyEncryptedCopy(encrypted, passphrase)
        deleteSidecars(source)

        check(source.renameTo(backup)) { "Could not stage plaintext database migration" }
        if (!encrypted.renameTo(source)) {
            backup.renameTo(source)
            throw IllegalStateException("Could not activate encrypted database migration")
        }

        securelyDelete(backup)
        deleteSidecars(backup)
    }

    internal fun hasPlaintextHeader(file: File): Boolean {
        if (!file.isFile || file.length() < sqliteHeader.size) return false
        val header = ByteArray(sqliteHeader.size)
        FileInputStream(file).use { input ->
            if (input.read(header) != header.size) return false
        }
        return header.contentEquals(sqliteHeader)
    }

    private fun exportEncryptedCopy(source: File, target: File, passphrase: ByteArray) {
        val database = SQLiteDatabase.openDatabase(
            source.absolutePath,
            ByteArray(0),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        var attached = false
        try {
            database.rawExecSQL("PRAGMA wal_checkpoint(FULL)")
            val version = database.rawQuery("PRAGMA user_version", emptyArray<String>()).use {
                check(it.moveToFirst()) { "Could not read plaintext database version" }
                it.getInt(0)
            }
            val targetPath = target.absolutePath.replace("'", "''")
            val keyHex = passphrase.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            database.rawExecSQL("ATTACH DATABASE '$targetPath' AS encrypted KEY \"x'$keyHex'\"")
            attached = true
            database.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { cursor ->
                cursor.moveToFirst()
            }
            database.rawExecSQL("PRAGMA encrypted.user_version = $version")
        } finally {
            if (attached) runCatching { database.rawExecSQL("DETACH DATABASE encrypted") }
            database.close()
        }
    }

    private fun verifyEncryptedCopy(file: File, passphrase: ByteArray) {
        check(file.isFile && file.length() > 0 && !hasPlaintextHeader(file)) {
            "Encrypted database export was not created"
        }
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        ).use { database ->
            database.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use {
                check(it.moveToFirst()) { "Encrypted database verification failed" }
            }
        }
    }

    private fun deleteDatabaseFiles(file: File) {
        file.delete()
        deleteSidecars(file)
    }

    private fun deleteSidecars(file: File) {
        File("${file.absolutePath}-wal").delete()
        File("${file.absolutePath}-shm").delete()
        File("${file.absolutePath}-journal").delete()
    }

    private fun securelyDelete(file: File) {
        if (!file.exists()) return
        runCatching {
            RandomAccessFile(file, "rw").use { randomAccess ->
                val zeros = ByteArray(64 * 1024)
                var remaining = randomAccess.length()
                randomAccess.seek(0)
                while (remaining > 0) {
                    val count = minOf(zeros.size.toLong(), remaining).toInt()
                    randomAccess.write(zeros, 0, count)
                    remaining -= count
                }
                randomAccess.fd.sync()
            }
        }
        file.delete()
    }
}
