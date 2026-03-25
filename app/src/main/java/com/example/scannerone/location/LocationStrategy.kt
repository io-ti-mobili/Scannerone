package com.example.scannerone.location

import com.example.scannerone.entities.WifiScanRecord

data class PositionEstimate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

interface LocationStrategy {
    /**
     * Calculates the estimated position of a router based on a list of scan records.
     * @return the estimated position, or null if it cannot be calculated.
     */
    fun calculatePosition(scans: List<WifiScanRecord>): PositionEstimate?
}
