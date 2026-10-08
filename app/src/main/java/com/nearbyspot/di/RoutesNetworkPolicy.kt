package com.nearbyspot.di

import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

internal object RoutesNetworkPolicy {
    const val API_KEY_HEADER = "X-Goog-Api-Key"

    fun addCredentials(request: Request, apiKey: String): Request =
        request.newBuilder()
            .header(API_KEY_HEADER, apiKey)
            .build()

    fun loggingLevel(isDebug: Boolean): HttpLoggingInterceptor.Level =
        if (isDebug) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
}
