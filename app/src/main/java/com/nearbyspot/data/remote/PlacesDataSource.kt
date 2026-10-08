package com.nearbyspot.data.remote

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place as GmsPlace
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.nearbyspot.domain.model.PhotoRef
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.Review
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlacesDataSource @Inject constructor(
    private val placesClient: PlacesClient,
) {
    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        placeTypes: List<String>,
    ): List<Place> = suspendCancellableCoroutine { cont ->
        val center = LatLng(latitude, longitude)
        val bounds = CircularBounds.newInstance(center, radiusMeters)

        val request = SearchNearbyRequest.builder(bounds, FIELDS)
            .setIncludedTypes(placeTypes)
            .setMaxResultCount(MAX_RESULTS)
            .build()

        Log.d(TAG, "searchNearby at ($latitude,$longitude) r=$radiusMeters types=$placeTypes")
        placesClient.searchNearby(request)
            .addOnSuccessListener { response ->
                val places = response.places.map { it.toDomainPlace() }
                Log.d(TAG, "searchNearby returned ${places.size} places")
                cont.resume(places)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Nearby search failed at ($latitude,$longitude): ${e.message}", e)
                cont.resumeWithException(e)
            }
    }

    fun getPhotoMetadatas(placeId: String): List<PhotoMetadata> {
        return photoMetadataCache[placeId] ?: emptyList()
    }

    suspend fun fetchReviews(placeId: String): List<Review> = suspendCancellableCoroutine { cont ->
        val request = FetchPlaceRequest.builder(placeId, REVIEW_FIELDS).build()
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val reviews = response.place.reviews?.take(5)?.map { review ->
                    Review(
                        authorName = review.authorAttribution?.name ?: "Anonymous",
                        rating = review.rating,
                        text = review.text ?: "",
                        relativeTimeDescription = review.relativePublishTimeDescription ?: "",
                    )
                } ?: emptyList()
                cont.resume(reviews)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Fetch reviews failed", e)
                cont.resume(emptyList())
            }
    }

    @Suppress("DEPRECATION")
    private fun GmsPlace.toDomainPlace(): Place {
        val placeId = id ?: ""
        val metadatas = photoMetadatas?.take(5) ?: emptyList()

        if (metadatas.isNotEmpty()) {
            photoMetadataCache[placeId] = metadatas
        }

        val photoRefs = metadatas.map { meta ->
            PhotoRef(
                widthPx = meta.width,
                heightPx = meta.height,
                attributions = meta.authorAttributions?.asList()
                    ?.joinToString { it.name } ?: "",
            )
        }

        return Place(
            placeId = placeId,
            name = displayName ?: "Unknown",
            address = formattedAddress ?: "",
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            rating = rating ?: 0.0,
            userRatingCount = userRatingsTotal ?: 0,
            priceLevel = priceLevel,
            isOpen = runCatching { isOpen }.getOrNull(),
            photoRefs = photoRefs,
            reviews = emptyList(),
            types = placeTypes?.map { it.toString() } ?: emptyList(),
        )
    }

    companion object {
        private const val TAG = "PlacesDataSource"
        private const val MAX_RESULTS = 20

        private val FIELDS = listOf(
            GmsPlace.Field.ID,
            GmsPlace.Field.DISPLAY_NAME,
            GmsPlace.Field.FORMATTED_ADDRESS,
            GmsPlace.Field.LOCATION,
            GmsPlace.Field.RATING,
            GmsPlace.Field.USER_RATINGS_TOTAL,
            GmsPlace.Field.PRICE_LEVEL,
            GmsPlace.Field.CURRENT_OPENING_HOURS,
            GmsPlace.Field.UTC_OFFSET,
            GmsPlace.Field.PHOTO_METADATAS,
            GmsPlace.Field.TYPES,
        )

        private val REVIEW_FIELDS = listOf(GmsPlace.Field.REVIEWS)

        private val photoMetadataCache = java.util.concurrent.ConcurrentHashMap<String, List<PhotoMetadata>>()
    }
}
