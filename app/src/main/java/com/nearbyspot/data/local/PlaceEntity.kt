package com.nearbyspot.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_places",
    primaryKeys = ["cacheKey", "placeId"],
    indices = [
        Index(value = ["cacheKey", "lastFetchedEpochMs"]),
        Index(value = ["lastFetchedEpochMs"]),
    ],
)
data class PlaceEntity(
    val placeId: String,
    val placeJson: String,
    val cacheKey: String,
    val lastFetchedEpochMs: Long,
)
