package com.example.scannerone.nominatimApi

import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat")            lat: Double,
        @Query("lon")            lon: Double,
        @Query("format")         format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): NominatimResponse

    @GET("search")
    suspend fun forwardGeocode(
        @Query("q")              query: String,
        @Query("format")         format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit")          limit: Int = 1
    ): List<NominatimResponse>
}