package com.nearbyspot.data.remote

import com.nearbyspot.data.remote.dto.ComputeRoutesResponse
import com.nearbyspot.data.remote.dto.ComputeRoutesRequest
import com.nearbyspot.data.remote.dto.RouteMatrixElement
import com.nearbyspot.data.remote.dto.RouteMatrixRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface RoutesApiService {

    @POST("distanceMatrix/v2:computeRouteMatrix")
    suspend fun computeRouteMatrix(
        @Header("X-Goog-FieldMask") fieldMask: String =
            "originIndex,destinationIndex,duration,distanceMeters",
        @Body request: RouteMatrixRequest,
    ): List<RouteMatrixElement>

    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-FieldMask") fieldMask: String =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline",
        @Body request: ComputeRoutesRequest,
    ): ComputeRoutesResponse
}
