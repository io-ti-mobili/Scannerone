package com.example.scannerone.locationCalc

import com.example.scannerone.entities.WifiScanRecord
import kotlin.math.pow

class WeightedCentroidCalcStrategy(
    private val useGpsWeight: Boolean = false
) : LocationCalcStrategy {
    
    override fun calculatePosition(scans: List<WifiScanRecord>): PositionEstimate? {
        if (scans.isEmpty()) return null
        
        var totalWeight = 0.0
        var weightedLat = 0.0
        var weightedLng = 0.0
        var totalAccuracy = 0.0f
        
        for (scan in scans) {
            val wRssi = 10.0.pow(scan.rssi / 10.0)
            
            // Applica il nuovo divisore di gravità se abilitato: accuracy inesistente = impatto debolissimo
            val wGps = if (useGpsWeight && scan.scanAccuracy > 0f) {
                1.0 / (scan.scanAccuracy * scan.scanAccuracy)
            } else 1.0
            
            val weight = wRssi * wGps
            
            weightedLat += scan.scanLatitude * weight
            weightedLng += scan.scanLongitude * weight
            totalWeight += weight
            totalAccuracy += scan.scanAccuracy
        }
        
        if (totalWeight <= 0.0) return null
        
        val finalLat = weightedLat / totalWeight
        val finalLng = weightedLng / totalWeight
        
        var varianceSum = 0.0
        for (scan in scans) {
            val wRssi = 10.0.pow(scan.rssi / 10.0)
            val wGps = if (useGpsWeight && scan.scanAccuracy > 0f) {
                1.0 / (scan.scanAccuracy * scan.scanAccuracy)
            } else 1.0
            val weight = wRssi * wGps
            
            val dLat = (scan.scanLatitude - finalLat) * 111320.0
            val dLon = (scan.scanLongitude - finalLng) * 111320.0 * Math.cos(Math.toRadians(finalLat))
            val distSq = (dLat * dLat) + (dLon * dLon)
            
            varianceSum += weight * distSq
        }
        
        val stdDev = kotlin.math.sqrt(varianceSum / totalWeight).toFloat()
        val baseGpsError = totalAccuracy / scans.size
        
        // Vera incertezza: Deviazione dei punti + incertezza di fondo dello strumento (GPS)
        val apAccuracy = stdDev + baseGpsError
        
        return PositionEstimate(finalLat, finalLng, apAccuracy)
    }
}
