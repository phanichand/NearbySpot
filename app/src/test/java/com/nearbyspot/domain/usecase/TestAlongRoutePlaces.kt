package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Place

internal fun alongRoutePlace(
    id: String,
    rating: Double = 4.5,
    reachMinutes: Int = 15,
    reviewCount: Int = 1_000,
) = AlongRoutePlace(
    place = Place(
        placeId = id,
        name = id,
        address = "",
        latitude = 0.0,
        longitude = 0.0,
        rating = rating,
        userRatingCount = reviewCount,
        priceLevel = null,
        isOpen = true,
        photoRefs = emptyList(),
        reviews = emptyList(),
        types = emptyList(),
    ),
    reachableSeconds = reachMinutes * 60,
    detourSeconds = 0,
    nearestRoutePointIndex = 0,
    score = 0.0,
)
