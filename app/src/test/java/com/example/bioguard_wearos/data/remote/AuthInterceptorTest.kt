package com.example.bioguard_wearos.data.remote

import com.example.bioguard_wearos.data.local.BioGuardPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {

    private val apiUrl = "https://bioguard-api-lkvnq.ondigitalocean.app"

    private fun request(path: String): Request =
        Request.Builder().url("$apiUrl$path").build()

    private fun response(code: Int, request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `endpoints de Auth no llevan header Authorization`() {
        val prefs = mockk<BioGuardPreferences>()
        val interceptor = AuthInterceptor(prefs)
        val req = request("/api/Auth/login-codigo")
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns req
        every { chain.proceed(req) } returns response(200, req)

        interceptor.intercept(chain)

        coVerify(exactly = 0) { prefs.getJwtToken() }
    }

    @Test
    fun `agrega header Authorization cuando hay token`() {
        val prefs = mockk<BioGuardPreferences>()
        coEvery { prefs.getJwtToken() } returns "jwt-token"
        val interceptor = AuthInterceptor(prefs)
        val req = request("/api/Sensores/lectura")
        val chain = mockk<Interceptor.Chain>()
        val captured = mutableListOf<Request>()

        every { chain.request() } returns req
        every { chain.proceed(any()) } answers { captured.add(firstArg<Request>()); response(200, firstArg()) }

        interceptor.intercept(chain)

        assertEquals(1, captured.size)
        assertEquals("Bearer jwt-token", captured[0].header("Authorization"))
    }

    @Test
    fun `no agrega header Authorization cuando no hay token`() {
        val prefs = mockk<BioGuardPreferences>()
        coEvery { prefs.getJwtToken() } returns null
        val interceptor = AuthInterceptor(prefs)
        val req = request("/api/Sensores/lectura")
        val chain = mockk<Interceptor.Chain>()
        val captured = mutableListOf<Request>()

        every { chain.request() } returns req
        every { chain.proceed(any()) } answers { captured.add(firstArg<Request>()); response(200, firstArg()) }

        interceptor.intercept(chain)

        assertEquals(1, captured.size)
        assertNull(captured[0].header("Authorization"))
    }

    @Test
    fun `401 sin refresh token cierra la sesion`() {
        val prefs = mockk<BioGuardPreferences>()
        coEvery { prefs.getJwtToken() } returns "jwt-token"
        coEvery { prefs.getRefreshToken() } returns null
        coEvery { prefs.clearAuthToken() } returns Unit
        val interceptor = AuthInterceptor(prefs)
        val req = request("/api/Sensores/lectura")
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns req
        every { chain.proceed(any()) } answers { response(401, firstArg()) }

        interceptor.intercept(chain)

        coVerify(exactly = 1) { prefs.clearAuthToken() }
    }

    @Test
    fun `401 con refresh token vacio tambien cierra la sesion`() {
        val prefs = mockk<BioGuardPreferences>()
        coEvery { prefs.getJwtToken() } returns "jwt-token"
        coEvery { prefs.getRefreshToken() } returns ""
        coEvery { prefs.clearAuthToken() } returns Unit
        val interceptor = AuthInterceptor(prefs)
        val req = request("/api/Sensores/lectura")
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns req
        every { chain.proceed(any()) } answers { response(401, firstArg()) }

        interceptor.intercept(chain)

        coVerify(exactly = 1) { prefs.clearAuthToken() }
    }
}
