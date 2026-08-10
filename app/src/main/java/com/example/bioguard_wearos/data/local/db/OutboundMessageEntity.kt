package com.example.bioguard_wearos.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_messages")
data class OutboundMessageEntity(
    @PrimaryKey val id: String,
    val path: String,
    val payloadJson: String,
    val priority: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    @androidx.room.ColumnInfo(name = "source_message_id")
    val sourceMessageId: String = java.util.UUID.randomUUID().toString()
)
