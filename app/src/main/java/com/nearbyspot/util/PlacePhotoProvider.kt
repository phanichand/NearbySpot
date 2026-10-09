package com.nearbyspot.util

import android.graphics.Bitmap
import android.util.LruCache
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.data.remote.PlacesDataSource
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlacePhotoProvider @Inject constructor(
    private val placesClient: PlacesClient,
    private val placesDataSource: PlacesDataSource,
) {
    private val cache = object : LruCache<String, Bitmap>(bitmapCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / BYTES_PER_KILOBYTE).coerceAtLeast(1)
    }

    suspend fun getPhoto(placeId: String, photoIndex: Int = 0, maxWidth: Int = 800): Bitmap? {
        val cacheKey = "${placeId}_${photoIndex}_$maxWidth"
        cache.get(cacheKey)?.let { return it }

        val metadatas = placesDataSource.getPhotoMetadatas(placeId)
        if (photoIndex >= metadatas.size) return null

        val metadata = metadatas[photoIndex]
        val request = FetchPhotoRequest.builder(metadata)
            .setMaxWidth(maxWidth)
            .build()

        return suspendCancellableCoroutine { cont ->
            placesClient.fetchPhoto(request)
                .addOnSuccessListener { response ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val bitmap = response.bitmap
                    cache.put(cacheKey, bitmap)
                    cont.resume(bitmap)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    companion object {
        private const val BYTES_PER_KILOBYTE = 1024
        private const val MEMORY_FRACTION = 16
        private const val MIN_CACHE_SIZE_KB = 4 * 1024
        private const val MAX_CACHE_SIZE_KB = 24 * 1024

        private fun bitmapCacheSizeKb(): Int =
            (Runtime.getRuntime().maxMemory() / BYTES_PER_KILOBYTE / MEMORY_FRACTION)
                .coerceIn(MIN_CACHE_SIZE_KB.toLong(), MAX_CACHE_SIZE_KB.toLong())
                .toInt()
    }
}
