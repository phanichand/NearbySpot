package com.nearbyspot.di

import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesNetworkPolicyTest {

    @Test
    fun `credentials are sent in a header without changing the URL`() {
        val original = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .build()

        val authenticated = RoutesNetworkPolicy.addCredentials(original, "test-api-key")

        assertEquals(original.url, authenticated.url)
        assertNull(authenticated.url.queryParameter("key"))
        assertEquals(
            "test-api-key",
            authenticated.header(RoutesNetworkPolicy.API_KEY_HEADER),
        )
    }

    @Test
    fun `debug logging excludes request and response bodies`() {
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            RoutesNetworkPolicy.loggingLevel(isDebug = true),
        )
    }

    @Test
    fun `release logging is disabled`() {
        assertEquals(
            HttpLoggingInterceptor.Level.NONE,
            RoutesNetworkPolicy.loggingLevel(isDebug = false),
        )
    }
}
