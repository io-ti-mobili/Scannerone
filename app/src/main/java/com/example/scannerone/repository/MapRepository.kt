package com.example.scannerone.repository

import com.example.scannerone.database.MapDao
import com.example.scannerone.entities.WifiNetwork

class MapRepository(private val mapDao: MapDao) {
    suspend fun getNetworksInBoundingBox(
        north: Double,
        south: Double,
        east: Double,
        west: Double
    ): List<WifiNetwork> = mapDao.getNetworksInBoundingBox(north, south, east, west)
}
