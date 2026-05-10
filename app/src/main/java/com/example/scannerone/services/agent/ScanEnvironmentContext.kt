package com.example.scannerone.services.agent

/**
 * Rappresenta le "Credenze" (Beliefs) dell'Agente.
 * Mantiene il contesto aggiornato sull'ambiente circostante dopo ogni scansione.
 */
data class ScanEnvironmentContext(
    val totalNetworksInLastScan: Int = 0,
    val newNetworksInLastScan: Int = 0,
    val isGpsAccuracyGood: Boolean = true
) {
    /**
     * Tasso di novità: percentuale di reti nuove rispetto al totale rilevato.
     * Da 0.0 (nessuna rete nuova) a 1.0 (tutte reti nuove).
     */
    val noveltyRatio: Double
        get() = if (totalNetworksInLastScan == 0) 0.0
        else newNetworksInLastScan.toDouble() / totalNetworksInLastScan
}
