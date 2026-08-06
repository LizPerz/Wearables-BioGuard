package com.example.bioguard_wearos.data.remote

import okhttp3.CertificatePinner

/**
 * DevSecOps hardening: certificado pinning del backend.
 *
 * Defiende contra MITM incluso si una CA es comprometida o emite un
 * certificado fraudulento para nuestro dominio.
 *
 * PINs obtenidos del encadenamiento actual (SPKI SHA-256, base64):
 *  - Hoja:       `bioguard-api-lkvnq.ondigitalocean.app` (CN=ondigitalocean.app,
 *                expira 2026-10-23)
 *  - Intermedio: WE1 (Google Trust Services, expira 2029-02-20)
 *
 * OkHttp acepta cualquiera de los dos pins (or). Cuando la hoja rote, el
 * nuevo certificado seguirá encadenando a WE1, por lo que la app no deja
 * de funcionar entre rotaciones.
 *
 * ROTACIÓN OBLIGATORIA: antes de cada renovación de certificado (la hoja
 * actual caduca el 2026-10-23) recalcular los hashes y actualizar esta lista
 * en la misma release. Para recalcular:
 *
 *   openssl s_client -connect bioguard-api-lkvnq.ondigitalocean.app:443 \
 *     -servername bioguard-api-lkvnq.ondigitalocean.app -showcerts </dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary \
 *     | openssl enc -base64
 */
object TlsSecurity {

    private const val API_HOST = "bioguard-api-lkvnq.ondigitalocean.app"

    /** PIN del certificado hoja actual (valido hasta 2026-10-23). */
    private const val PIN_LEAF =
        "sha256/gB5Zz2YuJeO41emziCFVPC0PYRQBOnTnU1CJz9RCuIQ="

    /** PIN de respaldo del intermedio WE1 (Google Trust Services). */
    private const val PIN_INTERMEDIATE_WE1 =
        "sha256/H7AMYAvicN2+UcFPBz3kJXCDmGrTItZh4ujUBK8hoWg="

    val certificatePinner: CertificatePinner = CertificatePinner.Builder()
        .add(API_HOST, PIN_LEAF, PIN_INTERMEDIATE_WE1)
        .build()
}
