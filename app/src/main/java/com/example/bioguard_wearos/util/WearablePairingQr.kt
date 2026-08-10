package com.example.bioguard_wearos.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

object WearablePairingQr {
    const val TYPE = "bioguard_wearable_pairing"
    const val VERSION = "2"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bioguard_wearable_pairing_identity_v2"

    fun canonicalPayload(
        deviceName: String,
        deviceId: String,
        nodeId: String,
        issuedAt: Long,
        nonce: String,
        publicKey: String
    ): String = listOf(
        "type=$TYPE",
        "version=$VERSION",
        "name=$deviceName",
        "address=$deviceId",
        "nodeId=$nodeId",
        "issuedAt=$issuedAt",
        "nonce=$nonce",
        "publicKey=$publicKey"
    ).joinToString("&")

    fun sign(canonicalPayload: String, privateKey: PrivateKey): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
        return encodeBase64(signer.sign())
    }

    fun newNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return encodeBase64(bytes)
    }

    fun buildPayload(
        deviceName: String,
        deviceId: String,
        nodeId: String,
        issuedAt: Long = System.currentTimeMillis() / 1000L,
        nonce: String = newNonce()
    ): String = buildPayload(deviceName, deviceId, nodeId, issuedAt, nonce, getOrCreateIdentity())

    fun buildPayload(
        deviceName: String,
        deviceId: String,
        nodeId: String,
        issuedAt: Long,
        nonce: String,
        identity: KeyPair
    ): String {
        val publicKey = encodeBase64(identity.public.encoded)
        val canonical = canonicalPayload(deviceName, deviceId, nodeId, issuedAt, nonce, publicKey)
        val query = linkedMapOf(
            "type" to TYPE,
            "version" to VERSION,
            "name" to deviceName,
            "address" to deviceId,
            "nodeId" to nodeId,
            "issuedAt" to issuedAt.toString(),
            "nonce" to nonce,
            "publicKey" to publicKey,
            "signature" to sign(canonical, identity.private)
        ).entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "bioguard://wearable-pair?$query"
    }

    private fun getOrCreateIdentity(): KeyPair {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        if (privateKey != null && publicKey != null) return KeyPair(publicKey, privateKey)

        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKeyPair()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun encodeBase64(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
