package com.nearbyspot.di

import android.content.Context
import androidx.room.Room
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.gson.Gson
import com.nearbyspot.BuildConfig
import com.nearbyspot.data.local.MIGRATION_3_4
import com.nearbyspot.data.local.NearbySpotDatabase
import com.nearbyspot.data.local.PlaceDao
import com.nearbyspot.data.remote.RoutesApiService
import com.nearbyspot.data.repository.PlaceRepositoryImpl
import com.nearbyspot.domain.repository.PlaceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient {
        return Places.createClient(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val apiKeyInterceptor = Interceptor { chain ->
            chain.proceed(
                RoutesNetworkPolicy.addCredentials(
                    request = chain.request(),
                    apiKey = BuildConfig.MAPS_API_KEY,
                )
            )
        }

        val logging = HttpLoggingInterceptor().apply {
            redactHeader(RoutesNetworkPolicy.API_KEY_HEADER)
            level = RoutesNetworkPolicy.loggingLevel(BuildConfig.DEBUG)
        }

        return OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRoutesApiService(client: OkHttpClient): RoutesApiService {
        return Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RoutesApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NearbySpotDatabase {
        return Room.databaseBuilder(
            context,
            NearbySpotDatabase::class.java,
            "nearbyspot_db"
        ).addMigrations(MIGRATION_3_4).build()
    }

    @Provides
    fun providePlaceDao(db: NearbySpotDatabase): PlaceDao = db.placeDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository
}
