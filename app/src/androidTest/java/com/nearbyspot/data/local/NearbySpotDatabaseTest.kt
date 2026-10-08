package com.nearbyspot.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbySpotDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NearbySpotDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: NearbySpotDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration3To4_preservesFavoritesAndClearsOnlyCachedPlaces() {
        migrationHelper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                "INSERT INTO `favorites` (`placeId`, `placeJson`, `savedAtEpochMs`) " +
                    "VALUES ('favorite-id', '{favorite}', 100)",
            )
            execSQL(
                "INSERT INTO `cached_places` " +
                    "(`placeId`, `placeJson`, `cacheKey`, `lastFetchedEpochMs`) " +
                    "VALUES ('cached-id', '{cached}', 'old-key', 100)",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            MIGRATION_3_4,
        )

        migrated.query("SELECT `placeId` FROM `favorites`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("favorite-id", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        migrated.query("SELECT COUNT(*) FROM `cached_places`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun cacheStoresTheSamePlaceForDifferentSearchKeys() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, NearbySpotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database = db

        db.placeDao().insertPlaces(
            listOf(
                cachedPlace(cacheKey = "first-search", fetchedAt = 200),
                cachedPlace(cacheKey = "second-search", fetchedAt = 300),
            ),
        )

        assertEquals(
            listOf("shared-place"),
            db.placeDao().getCachedPlaces("first-search", minEpoch = 0)
                .map(PlaceEntity::placeId),
        )
        assertEquals(
            listOf("shared-place"),
            db.placeDao().getCachedPlaces("second-search", minEpoch = 0)
                .map(PlaceEntity::placeId),
        )
    }

    private fun cachedPlace(cacheKey: String, fetchedAt: Long) = PlaceEntity(
        placeId = "shared-place",
        placeJson = "{}",
        cacheKey = cacheKey,
        lastFetchedEpochMs = fetchedAt,
    )

    private companion object {
        const val TEST_DATABASE = "nearbyspot-migration-test"
    }
}
