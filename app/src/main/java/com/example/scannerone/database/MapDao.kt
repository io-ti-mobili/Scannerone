package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Query
import com.example.scannerone.entities.WifiNetwork

@Dao
interface MapDao {
    @Query(
        """
        SELECT * FROM wifi_networks
        WHERE realLatitude BETWEEN :south AND :north
        AND realLongitude BETWEEN :west AND :east
        """
    )
    suspend fun getNetworksInBoundingBox(
        north: Double,
        south: Double,
        east: Double,
        west: Double
    ): List<WifiNetwork>
}
