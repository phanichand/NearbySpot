package com.nearbyspot.domain.model

data class Place(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val userRatingCount: Int,
    val priceLevel: Int?,
    val isOpen: Boolean?,
    val photoRefs: List<PhotoRef>,
    val reviews: List<Review>,
    val types: List<String>,
)

data class PhotoRef(
    val widthPx: Int,
    val heightPx: Int,
    val attributions: String,
)

data class Review(
    val authorName: String,
    val rating: Double,
    val text: String,
    val relativeTimeDescription: String,
)
