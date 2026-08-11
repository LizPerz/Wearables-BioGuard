package com.example.bioguard_wearos.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsSecurityTest {

    private val apiHost = "bioguard-api-lkvnq.ondigitalocean.app"

    @Test
    fun `certificatePinner define pins de hoja e intermedio para el backend`() {
        val pins = TlsSecurity.certificatePinner.findMatchingPins(apiHost)
        assertTrue("Debe haber al menos dos pins (hoja + intermedio)", pins.size >= 2)
    }

    @Test
    fun `todos los pins son sha256 con hash de 32 bytes`() {
        val pins = TlsSecurity.certificatePinner.findMatchingPins(apiHost)
        assertTrue("Debe haber pins configurados", pins.isNotEmpty())
        pins.forEach { pin ->
            assertEquals("sha256", pin.hashAlgorithm)
            assertEquals(32, pin.hash.size)
        }
    }

    @Test
    fun `no hay pins para hosts fuera del backend`() {
        assertTrue(TlsSecurity.certificatePinner.findMatchingPins("google.com").isEmpty())
        assertTrue(TlsSecurity.certificatePinner.findMatchingPins("localhost").isEmpty())
    }
}
