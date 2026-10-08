package com.nearbyspot.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val placeId: String,
    val placeJson: String,
    val savedAtEpochMs: Long,
)
