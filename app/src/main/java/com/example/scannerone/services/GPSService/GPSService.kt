package com.example.scannerone.services.GPSService

data class Position(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getAge(): Long = System.currentTimeMillis() - timestamp
}

interface GPSService {
    suspend fun getPosition(): Position
}
