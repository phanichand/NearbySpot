package com.nearbyspot.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaceDao {
    @Query("SELECT * FROM cached_places WHERE placeId = :placeId AND lastFetchedEpochMs > :minEpoch")
    suspend fun getCachedPlace(placeId: String, minEpoch: Long): PlaceEntity?

    @Query("SELECT * FROM cached_places WHERE cacheKey = :cacheKey AND lastFetchedEpochMs > :minEpoch")
    suspend fun getCachedPlaces(cacheKey: String, minEpoch: Long): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Query("DELETE FROM cached_places WHERE lastFetchedEpochMs < :olderThan")
    suspend fun deleteStale(olderThan: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE placeId = :placeId")
    suspend fun deleteFavorite(placeId: String)

    @Query("SELECT * FROM favorites ORDER BY savedAtEpochMs DESC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE placeId = :placeId)")
    suspend fun isFavorite(placeId: String): Boolean

    @Query("SELECT placeId FROM favorites")
    fun observeFavoriteIds(): kotlinx.coroutines.flow.Flow<List<String>>
}
