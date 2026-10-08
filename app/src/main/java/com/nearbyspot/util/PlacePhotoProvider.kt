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
    private val cache = LruCache<String, Bitmap>(MAX_CACHE_ENTRIES)

    suspend fun getPhoto(placeId: String, photoIndex: Int = 0, maxWidth: Int = 800): Bitmap? {
        val cacheKey = "${placeId}_${photoIndex}"
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
                    val bitmap = response.bitmap
                    cache.put(cacheKey, bitmap)
                    cont.resume(bitmap)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 50
    }
}
