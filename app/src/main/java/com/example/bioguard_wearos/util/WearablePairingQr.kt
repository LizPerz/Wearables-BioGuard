package com.example.bioguard_wearos.util

import com.example.bioguard_wearos.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WearablePairingQr {
    const val TYPE = "bioguard_wearable_pairing"
    const val VERSION = "1"

    fun canonicalPayload(
        deviceName: String,
        deviceId: String,
        nodeId: String,
        issuedAt: Long,
        nonce: String
    ): String = listOf(
        "type=$TYPE",
        "version=$VERSION",
        "name=$deviceName",
        "address=$deviceId",
        "nodeId=$nodeId",
        "issuedAt=$issuedAt",
        "nonce=$nonce"
    ).joinToString("&")

    fun sign(canonicalPayload: String, secret: String = BuildConfig.BIOGUARD_PAIRING_SECRET): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonicalPayload.toByteArray()))
    }

    fun newNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun buildPayload(
        deviceName: String,
        deviceId: String,
        nodeId: String,
        issuedAt: Long = System.currentTimeMillis() / 1000L,
        nonce: String = newNonce()
    ): String {
        val canonical = canonicalPayload(deviceName, deviceId, nodeId, issuedAt, nonce)
        val signature = sign(canonical)
        val query = mapOf(
            "type" to TYPE,
            "version" to VERSION,
            "name" to deviceName,
            "address" to deviceId,
            "nodeId" to nodeId,
            "issuedAt" to issuedAt.toString(),
            "nonce" to nonce,
            "signature" to signature
        ).entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "bioguard://wearable-pair?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
