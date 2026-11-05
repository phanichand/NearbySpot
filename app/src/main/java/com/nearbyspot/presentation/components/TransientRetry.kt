package com.nearbyspot.presentation.components

import com.google.android.gms.common.api.ApiException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val NETWORK_ERROR = 7
private const val INTERNAL_ERROR = 8
private const val TIMEOUT = 15
private const val DEFAULT_RETRY_DELAY_MS = 400L

/** Failures that usually clear on their own, such as a brief network drop. */
internal fun isTransientPlacesError(error: Exception): Boolean = when (error) {
    is ApiException -> error.statusCode in setOf(NETWORK_ERROR, INTERNAL_ERROR, TIMEOUT)
    is IOException -> true
    else -> false
}

/** Runs [block], retrying once after [delayMs] if it fails with a transient error. */
internal suspend fun <T> withTransientRetry(
    delayMs: Long = DEFAULT_RETRY_DELAY_MS,
    block: suspend () -> T,
): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    if (!isTransientPlacesError(error)) throw error
    delay(delayMs)
    block()
}
