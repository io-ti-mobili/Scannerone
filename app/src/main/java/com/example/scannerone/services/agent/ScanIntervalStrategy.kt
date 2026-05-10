package com.example.scannerone.services.agent

/**
 * Rappresenta le "Intenzioni" (Intentions) dell'Agente.
 * Definisce la strategia per calcolare quanto aspettare per il prossimo scan.
 */
interface ScanIntervalStrategy {
    fun calculateNextInterval(context: ScanEnvironmentContext): Long
}

/**
 * Strategia Pigra/Costante: Ignora il contesto e aspetta sempre un tempo fisso.
 * Ideale per quando si è fermi o quando si viaggia troppo veloci (auto).
 */
class FixedScanStrategy(private val intervalMs: Long) : ScanIntervalStrategy {
    override fun calculateNextInterval(context: ScanEnvironmentContext): Long = intervalMs
}

/**
 * Strategia Intelligente: Adatta il tempo di attesa in base alla novità dell'ambiente.
 * Ideale quando si cammina.
 */
class AdaptiveScanStrategy(
    private val minMs: Long,
    private val maxMs: Long
) : ScanIntervalStrategy {
    override fun calculateNextInterval(context: ScanEnvironmentContext): Long {
        // Se troviamo almeno il 25% di reti nuove, consideriamo la zona "molto nuova" (moltiplicatore 1.0)
        // Coerce limitato a 0.25 per evitare che serva il 100% di reti nuove per abbassare il timer al minimo.
        val effectiveNovelty = (context.noveltyRatio.coerceIn(0.0, 0.25) * 4.0)
        
        val range = maxMs - minMs
        val reduction = (range * effectiveNovelty).toLong()
        
        // Se novità è alta, sottraiamo molta attesa avvicinandoci a minMs.
        // Se novità è 0, la riduzione è 0, quindi aspettiamo maxMs.
        return (maxMs - reduction).coerceIn(minMs, maxMs)
    }
}
