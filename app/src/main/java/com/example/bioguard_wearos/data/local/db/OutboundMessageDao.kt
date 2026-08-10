package com.example.bioguard_wearos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboundMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: OutboundMessageEntity): Long

    @Query("SELECT * FROM outbound_messages ORDER BY priority ASC, createdAt ASC LIMIT 1")
    suspend fun next(): OutboundMessageEntity?

    @Query("DELETE FROM outbound_messages WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE outbound_messages SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: String): Int

    @Query("SELECT COUNT(*) FROM outbound_messages")
    suspend fun count(): Int
}
