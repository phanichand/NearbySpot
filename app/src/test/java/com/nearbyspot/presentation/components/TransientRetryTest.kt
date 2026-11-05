package com.nearbyspot.presentation.components

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientRetryTest {

    private fun api(code: Int) = ApiException(Status(code))

    @Test
    fun `network timeout and internal errors are transient`() {
        assertTrue(isTransientPlacesError(api(7)))
        assertTrue(isTransientPlacesError(api(8)))
        assertTrue(isTransientPlacesError(api(15)))
        assertTrue(isTransientPlacesError(IOException("reset")))
    }

    @Test
    fun `denied and invalid requests are not transient`() {
        assertFalse(isTransientPlacesError(api(9011)))
        assertFalse(isTransientPlacesError(api(3)))
        assertFalse(isTransientPlacesError(IllegalStateException("bug")))
    }

    @Test
    fun `a transient failure is retried once and the second result is returned`() = runTest {
        var calls = 0

        val result = withTransientRetry(delayMs = 0) {
            calls++
            if (calls == 1) throw api(7) else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `a second transient failure is thrown after exactly one retry`() = runTest {
        var calls = 0

        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                withTransientRetry(delayMs = 0) {
                    calls++
                    throw api(7)
                }
            }
        }

        assertEquals(2, calls)
    }

    @Test
    fun `a non transient failure is not retried`() = runTest {
        var calls = 0

        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                withTransientRetry(delayMs = 0) {
                    calls++
                    throw api(9011)
                }
            }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `cancellation is never retried`() = runTest {
        var calls = 0

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                withTransientRetry(delayMs = 0) {
                    calls++
                    throw CancellationException("cancelled")
                }
            }
        }

        assertEquals(1, calls)
    }
}
