package com.nearbyspot.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, FavoriteEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class NearbySpotDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
}
