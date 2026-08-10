package com.example.bioguard_wearos.data.bluetooth

import android.util.Log
import com.example.bioguard_wearos.data.local.db.OutboundMessageDao
import com.example.bioguard_wearos.data.local.db.OutboundMessageEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class MessagePriority(val level: Int) {
    EMERGENCY(1), ALERT(2), TELEMETRY(3), HEARTBEAT(4)
}

@Singleton
class PriorityMessageQueue @Inject constructor(
    private val dao: OutboundMessageDao
) {
    companion object {
        private const val TAG = "BIOGUARD_PRIORITY_QUEUE"
        private const val MAX_ATTEMPTS_PER_RUN = 5
    }

    private val consumerMutex = Mutex()

    suspend fun enqueue(
        id: String,
        path: String,
        payloadJson: String,
        priority: MessagePriority,
        sendAction: suspend (path: String, payloadJson: String) -> Boolean
    ) {
        dao.insert(
            OutboundMessageEntity(
                id = id,
                path = path,
                payloadJson = payloadJson,
                priority = priority.level
            )
        )
        Log.d(TAG, "Mensaje persistido [${priority.name}]: $path")
        drain(sendAction)
    }

    suspend fun drain(sendAction: suspend (path: String, payloadJson: String) -> Boolean) {
        consumerMutex.withLock {
            while (true) {
                val message = dao.next() ?: return
                var delivered = false
                repeat(MAX_ATTEMPTS_PER_RUN) { attempt ->
                    if (delivered) return@repeat
                    delivered = runCatching { sendAction(message.path, message.payloadJson) }
                        .onFailure { Log.w(TAG, "Error enviando ${message.path}: ${it.message}") }
                        .getOrDefault(false)
                    if (!delivered) {
                        dao.incrementAttempts(message.id)
                        delay(1_000L shl attempt.coerceAtMost(4))
                    }
                }
                if (!delivered) {
                    Log.w(TAG, "Mensaje conservado para reintento posterior: ${message.path}")
                    return
                }
                dao.delete(message.id)
                Log.d(TAG, "Mensaje confirmado y retirado de cola: ${message.path}")
            }
        }
    }
}
