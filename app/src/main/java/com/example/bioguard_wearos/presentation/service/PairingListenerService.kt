package com.example.bioguard_wearos.presentation.service

import android.util.Log
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class PairingListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PAIR_PATH) return
        scope.launch {
            runCatching {
                require(event.data.size <= MAX_PAIRING_MESSAGE_BYTES) { "Pairing message is too large" }
                val request = JSONObject(String(event.data, Charsets.UTF_8))
                require(request.optInt("protocolVersion") == 2) { "Unsupported pairing protocol" }
                val requestId = request.optString("requestId")
                require(requestId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "Invalid pairing request ID" }
                val isNearby = Wearable.getNodeClient(applicationContext).connectedNodes.await()
                    .any { it.id == event.sourceNodeId && it.isNearby }
                require(isNearby) { "Pairing source is not nearby" }
                val nonce = request.optString("nonce")
                val preferences = BioGuardPreferences(applicationContext)
                val trustedNodeId = preferences.getTrustedMobileNodeId()
                require(!isPairingBlockedByExclusivity(trustedNodeId, event.sourceNodeId)) {
                    "El reloj ya esta vinculado a otro telefono; desvincula desde el reloj para cambiar de movil"
                }
                if (trustedNodeId != event.sourceNodeId) {
                    if (nonce.isNotBlank() && !nonce.startsWith("auto-pair")) {
                        preferences.consumePairingChallenge(nonce)
                    }
                }
                preferences.saveTrustedMobileNodeId(event.sourceNodeId)
                val ack = JSONObject()
                    .put("protocolVersion", 2)
                    .put("requestId", requestId)
                    .put("accepted", true)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(event.sourceNodeId, PAIR_ACK_PATH, ack)
                    .await()
            }.onSuccess {
                Log.i(TAG, "Movil de confianza vinculado mediante reto QR de un solo uso")
            }.onFailure {
                Log.w(TAG, "Solicitud de vinculacion rechazada: ${it.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "BIOGUARD_PAIRING"
        const val PAIR_PATH = "/bioguard/pair"
        const val PAIR_ACK_PATH = "/bioguard/pair/ack"
        const val MAX_PAIRING_MESSAGE_BYTES = 1024
    }
}

internal fun isPairingBlockedByExclusivity(trustedNodeId: String?, sourceNodeId: String): Boolean =
    !trustedNodeId.isNullOrBlank() && trustedNodeId != sourceNodeId
