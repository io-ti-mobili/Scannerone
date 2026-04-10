package com.example.scannerone.locationCalc

import com.example.scannerone.entities.WifiScanRecord
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

class TrilaterationCalcStrategy(
    private val useGpsWeight: Boolean = false,
    private val txPower: Double = -30.0, // Base power at 1 meter
    private val pathLossExponent: Double = 2.5 // Environment constant (2.5 is typical for mixed indoor/outdoor)
) : LocationCalcStrategy {

    override fun calculatePosition(scans: List<WifiScanRecord>): PositionEstimate? {
        // Trilateration requires at least 3 points to intersect circles reliably
        if (scans.size < 3) {
            // Fallback to simpler centroid se non c'è abbastanza tridimensionalità
            return WeightedCentroidCalcStrategy(useGpsWeight).calculatePosition(scans)
        }

        val lat0 = scans[0].scanLatitude
        val lon0 = scans[0].scanLongitude
        val latToMeters = 111320.0
        val lonToMeters = 111320.0 * cos(lat0 * Math.PI / 180.0)

        // Triplettando posizione con distanza stimata + salvataggio index per pesatura
        val points = scans.mapIndexed { index, scan ->
            val x = (scan.scanLongitude - lon0) * lonToMeters
            val y = (scan.scanLatitude - lat0) * latToMeters
            val distance = 10.0.pow((txPower - scan.rssi) / (10.0 * pathLossExponent))
            
            // Calcolo peso GPS separato da agganciare alla matrice dei LeastSquares
            val wGps = if (useGpsWeight && scan.scanAccuracy > 0f) {
                1.0 / (scan.scanAccuracy * scan.scanAccuracy)
            } else 1.0
            
            Triple(x, y, distance) to wGps
        }

        val refPair = points.last()
        val ref = refPair.first
        
        var ata00 = 0.0
        var ata01 = 0.0
        var ata11 = 0.0
        var atb0 = 0.0
        var atb1 = 0.0

        for (i in 0 until points.size - 1) {
            val p = points[i].first
            val w = points[i].second // Il GPS Weight (Weighted Least Squares)
            
            val a0 = 2.0 * (p.first - ref.first) * w
            val a1 = 2.0 * (p.second - ref.second) * w
            
            val b = (p.first.pow(2) - ref.first.pow(2) +
                    p.second.pow(2) - ref.second.pow(2) -
                    p.third.pow(2) + ref.third.pow(2)) * w

            // Moltiplicazione A^T * A e A^T * B cumulativa (Minimi Quadrati)
            ata00 += a0 * a0
            ata01 += a0 * a1
            ata11 += a1 * a1

            atb0 += a0 * b
            atb1 += a1 * b
        }
        val ata10 = ata01

        val det = ata00 * ata11 - ata01 * ata10
        if (abs(det) < 1e-7) {
            // I punti formano una linea retta o sono sovrapposti (impossibile calcolare intersezione)
            return WeightedCentroidCalcStrategy(useGpsWeight).calculatePosition(scans)
        }

        // Inversione della matrice 2x2 e calcolo finale [X, Y]
        val inv00 = ata11 / det
        val inv01 = -ata01 / det
        val inv10 = -ata10 / det
        val inv11 = ata00 / det

        val xResult = inv00 * atb0 + inv01 * atb1
        val yResult = inv10 * atb0 + inv11 * atb1

        // Riconversione dai metri XY in Lat/Lon
        val finalLat = lat0 + (yResult / latToMeters)
        var finalLon = lon0 + (xResult / lonToMeters)

        // Calcolo della deviazione geometrica (quanto i punti concordavano sull'esatto incrocio)
        var varianceSum = 0.0
        var totalW = 0.0
        var avgGpsAccuracy = 0.0f

        for (scan in scans) {
            val wRssi = 10.0.pow(scan.rssi / 10.0)
            val wGps = if (useGpsWeight && scan.scanAccuracy > 0f) {
                1.0 / (scan.scanAccuracy * scan.scanAccuracy)
            } else 1.0
            val weight = wRssi * wGps
            
            val dLat = (scan.scanLatitude - finalLat) * latToMeters
            val dLon = (scan.scanLongitude - finalLon) * lonToMeters
            val distSq = (dLat * dLat) + (dLon * dLon)
            
            varianceSum += weight * distSq
            totalW += weight
            avgGpsAccuracy += scan.scanAccuracy
        }

        val stdDev = kotlin.math.sqrt(varianceSum / totalW).toFloat()
        val baseGpsError = avgGpsAccuracy / scans.size
        
        // Vera Incertezza (Dispersione Spaziale + Tappo Fisiologico del GPS del cellulare)
        val apAccuracy = stdDev + baseGpsError

        return PositionEstimate(finalLat, finalLon, apAccuracy)
    }
}
