package com.example.scannerone.locationCalc

import com.example.scannerone.entities.WifiScanRecord
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Toglie i dati sporchi usando il concetto "Random Sample Consensus" e passa i dati puliti (Inliers) all'algoritmo matematico finale.
 */
class RansacCalcStrategyWrapper(
    private val baseStrategy: LocationCalcStrategy,
    private val iterations: Int = 50,
    private val kSamples: Int = 5,
    private val thresholdMeters: Double = 30.0
) : LocationCalcStrategy {

    override fun calculatePosition(scans: List<WifiScanRecord>): PositionEstimate? {
        // RANSAC ha bisogno di un po' di dati per tirare a indovinare
        if (scans.size < 5) {
            return baseStrategy.calculatePosition(scans)
        }

        var bestInliers = emptyList<WifiScanRecord>()

        repeat(iterations) {
            // Pesca campioni a caso
            val seed = scans.shuffled().take(kSamples)
            
            // Baricentro grezzo per questo gruppetto
            val seedLat = seed.map { it.scanLatitude }.average()
            val seedLon = seed.map { it.scanLongitude }.average()

            // Contiamo i fedeli (chi è vicino a questo baricentro provvisorio entro X metri)
            val inliers = scans.filter { s ->
                distanceMeters(s.scanLatitude, s.scanLongitude, seedLat, seedLon) < thresholdMeters
            }

            // Se questo gruppetto ha trovato più fedeli del record precedente, diventa il nuovo standard!
            if (inliers.size > bestInliers.size) {
                bestInliers = inliers
            }
        }

        // Se RANSAC ha fallito (meno di 5 inliers trovati), usiamo tutti i dati. Altrimenti diamo i dati passati al setaccio.
        val finalScans = if (bestInliers.size >= 5) bestInliers else scans
        
        // Passiamo gli inliers alla strategia base (es. Trilateration o Centroid)!
        return baseStrategy.calculatePosition(finalScans)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat1 - lat2) * 111320.0
        val dLon = (lon1 - lon2) * 111320.0 * cos(Math.toRadians(lat1))
        return sqrt(dLat * dLat + dLon * dLon)
    }
}
