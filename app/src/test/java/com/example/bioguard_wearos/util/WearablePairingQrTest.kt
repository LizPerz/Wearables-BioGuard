package com.example.bioguard_wearos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class WearablePairingQrTest {
    private fun identity() = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    @Test
    fun buildPayload_includesVerifiableAsymmetricSignature() {
        val issuedAt = 1_800_000_000L
        val nonce = "0123456789abcdef"
        val identity = identity()
        val payload = WearablePairingQr.buildPayload(
            "BioGuard Watch", "node-address-123", "node-id-456", issuedAt, nonce, identity
        )
        val uri = URI(payload)
        val params = uri.rawQuery.split("&").associate {
            val pieces = it.split("=", limit = 2)
            URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
        }
        val canonical = WearablePairingQr.canonicalPayload(
            "BioGuard Watch", "node-address-123", "node-id-456", issuedAt, nonce,
            params.getValue("publicKey")
        )
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(identity.public)
            update(canonical.toByteArray(StandardCharsets.UTF_8))
            verify(Base64.getUrlDecoder().decode(params.getValue("signature")))
        }

        assertEquals("2", params["version"])
        assertEquals(nonce, params["nonce"])
        assertTrue(verified)
    }

    @Test
    fun sign_changesWhenDeviceIdentityChanges() {
        val identity = identity()
        val original = WearablePairingQr.sign("address=a", identity.private)
        val tampered = WearablePairingQr.sign("address=b", identity.private)

        assertNotEquals(original, tampered)
    }
}
