package com.example.bioguard_wearos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class WearablePairingQrTest {
    @Test
    fun buildPayload_includesSignedPairingFields() {
        val issuedAt = 1_800_000_000L
        val nonce = "0123456789abcdef"
        val payload = WearablePairingQr.buildPayload(
            deviceName = "BioGuard Watch",
            deviceId = "node-address-123",
            nodeId = "node-id-456",
            issuedAt = issuedAt,
            nonce = nonce
        )
        val uri = URI(payload)
        val params = uri.rawQuery.split("&").associate {
            val pieces = it.split("=", limit = 2)
            URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
        }
        val canonical = WearablePairingQr.canonicalPayload(
            "BioGuard Watch",
            "node-address-123",
            "node-id-456",
            issuedAt,
            nonce
        )

        assertEquals("bioguard", uri.scheme)
        assertEquals("wearable-pair", uri.host)
        assertEquals(WearablePairingQr.TYPE, params["type"])
        assertEquals(WearablePairingQr.VERSION, params["version"])
        assertEquals(issuedAt.toString(), params["issuedAt"])
        assertEquals(nonce, params["nonce"])
        assertEquals(WearablePairingQr.sign(canonical), params["signature"])
    }

    @Test
    fun sign_changesWhenDeviceIdentityChanges() {
        val issuedAt = 1_800_000_000L
        val nonce = "0123456789abcdef"
        val original = WearablePairingQr.sign(
            WearablePairingQr.canonicalPayload("BioGuard Watch", "address-a", "node-a", issuedAt, nonce)
        )
        val tampered = WearablePairingQr.sign(
            WearablePairingQr.canonicalPayload("BioGuard Watch", "address-b", "node-a", issuedAt, nonce)
        )

        assertNotEquals(original, tampered)
        assertNotNull(original)
    }
}
