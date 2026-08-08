package com.example.bioguard_wearos.data.bluetooth

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

enum class MessagePriority(val level: Int) {
    EMERGENCY(1),   // Botón de pánico, Alertas nocturnas críticas
    ALERT(2),       // Estado de batería baja, alertas de dispositivo
    TELEMETRY(3),   // Lecturas de sensores periódicas
    HEARTBEAT(4)    // Heartbeat rutinario
}

data class QueuedMessage(
    val id: String,
    val path: String,
    val payloadJson: String,
    val priority: MessagePriority,
    val timestamp: Long = System.currentTimeMillis(),
    val sendAction: suspend (path: String, payloadJson: String) -> Boolean
) : Comparable<QueuedMessage> {
    override fun compareTo(other: QueuedMessage): Int {
        val priorityCompare = this.priority.level.compareTo(other.priority.level)
        if (priorityCompare != 0) return priorityCompare
        return this.timestamp.compareTo(other.timestamp)
    }
}

@Singleton
class PriorityMessageQueue @Inject constructor() {

    companion object {
        private const val TAG = "BIOGUARD_PRIORITY_QUEUE"
        private const val MAX_RETRIES = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = PriorityQueue<QueuedMessage>()
    private val lock = Any()
    @Volatile private var isProcessing = false

    fun enqueue(
        id: String,
        path: String,
        payloadJson: String,
        priority: MessagePriority,
        sendAction: suspend (path: String, payloadJson: String) -> Boolean
    ) {
        synchronized(lock) {
            val message = QueuedMessage(id, path, payloadJson, priority, sendAction = sendAction)
            queue.add(message)
            Log.d(TAG, "Mensaje encolado [Prioridad ${priority.name}]: $path (Total en cola: ${queue.size})")
        }
        processQueue()
    }

    private fun processQueue() {
        if (isProcessing) return
        scope.launch {
            isProcessing = true
            while (true) {
                val nextMessage: QueuedMessage = synchronized(lock) {
                    queue.poll()
                } ?: break

                var attempt = 0
                var success = false
                val action = nextMessage.sendAction
                val path = nextMessage.path
                val payload = nextMessage.payloadJson

                while (attempt < MAX_RETRIES && !success) {
                    attempt++
                    try {
                        success = action(path, payload)
                        if (success) {
                            Log.d(TAG, "Mensaje enviado con éxito: $path")
                        } else {
                            Log.w(TAG, "Fallo al enviar $path (Intento $attempt/$MAX_RETRIES)")
                            delay(1000L * attempt) // Backoff exponencial
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Excepción enviando $path: ${e.message}")
                        delay(1000L * attempt)
                    }
                }

                if (!success && nextMessage.priority == MessagePriority.EMERGENCY) {
                    Log.e(TAG, "FALLO CRÍTICO: No se pudo entregar mensaje de emergencia $path")
                }
            }
            isProcessing = false
        }
    }
}
