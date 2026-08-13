package com.example.bioguard_wearos.presentation.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingExclusivityTest {

    @Test
    fun permiteVinculacionCuandoNoHayMovilVinculado() {
        assertFalse(isPairingBlockedByExclusivity(null, "node-B"))
        assertFalse(isPairingBlockedByExclusivity("", "node-B"))
    }

    @Test
    fun permiteRevinculacionDelMismoMovil() {
        assertFalse(isPairingBlockedByExclusivity("node-A", "node-A"))
    }

    @Test
    fun bloqueaSegundoMovilCuandoYaHayUnoVinculado() {
        assertTrue(isPairingBlockedByExclusivity("node-A", "node-B"))
    }
}
