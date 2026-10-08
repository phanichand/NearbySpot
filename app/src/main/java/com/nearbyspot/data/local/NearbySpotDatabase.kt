package com.nearbyspot.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class NearbySpotDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
}
