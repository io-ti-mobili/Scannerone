package com.example.scannerone.services.GPSService

data class Position(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getAge(): Long = System.currentTimeMillis() - timestamp

    fun distanceTo(other: Position): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }
}

interface GPSService {
    suspend fun getPosition(): Position
}
