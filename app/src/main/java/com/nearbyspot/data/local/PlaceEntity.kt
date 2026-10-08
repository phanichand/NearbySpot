package com.nearbyspot.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_places")
data class PlaceEntity(
    @PrimaryKey val placeId: String,
    val placeJson: String,
    val cacheKey: String,
    val lastFetchedEpochMs: Long,
)
