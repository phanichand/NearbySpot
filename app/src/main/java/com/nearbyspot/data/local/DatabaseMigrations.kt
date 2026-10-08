package com.nearbyspot.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Cached place results are disposable. Recreate only that table so favorites survive the
 * migration while the cache adopts an identity that supports a place appearing in many searches.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `cached_places`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_places` (
                `placeId` TEXT NOT NULL,
                `placeJson` TEXT NOT NULL,
                `cacheKey` TEXT NOT NULL,
                `lastFetchedEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`cacheKey`, `placeId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_places_cacheKey_lastFetchedEpochMs` " +
                "ON `cached_places` (`cacheKey`, `lastFetchedEpochMs`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_places_lastFetchedEpochMs` " +
                "ON `cached_places` (`lastFetchedEpochMs`)",
        )
    }
}
