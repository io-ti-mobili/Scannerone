package com.example.scannerone.services.agent

import com.example.scannerone.services.WarDrivingService.WarDrivingScanResult
import com.example.scannerone.services.motion.MotionState

/**
 * L'Agente BDI principale: osserva l'ambiente e decide i tempi di scansione.
 */
class ScanDirectorAgent {
    // Le "Credenze" (Beliefs)
    private var currentContext = ScanEnvironmentContext()

    // Le "Strategie/Intenzioni" (Intentions)
    private val walkingStrategy = AdaptiveScanStrategy(minMs = 10_000L, maxMs = 60_000L)
    private val stillStrategy = FixedScanStrategy(intervalMs = 60_000L)
    private val vehicleStrategy = FixedScanStrategy(intervalMs = 10_000L)

    /**
     * L'agente percepisce l'esito dell'ultimo scan e aggiorna la sua visione del mondo.
     */
    fun updateBeliefs(result: WarDrivingScanResult) {
        currentContext = ScanEnvironmentContext(
            totalNetworksInLastScan = result.networksFound,
            newNetworksInLastScan = result.networksSaved
        )
    }

    /**
     * L'agente delibera quanto tempo deve passare prima della prossima scansione.
     */
    fun askNextScanInterval(currentState: MotionState): Long {
        val strategyToUse = when (currentState) {
            MotionState.Walking -> walkingStrategy
            MotionState.Still -> stillStrategy
            MotionState.InVehicle -> vehicleStrategy
        }
        return strategyToUse.calculateNextInterval(currentContext)
    }
}
