package com.example.scannerone.services.WarDrivingService

import android.net.wifi.ScanResult
import com.example.scannerone.services.GPSService.Position

/**
 * Risultato di un ciclo di scansione wardriving.
 */
data class WarDrivingScanResult(
    val networksFound: Int,
    val networksSaved: Int,
    val uniqueNetworksInSession: Int,
    val position: Position,
    val totalDistanceMetres: Double = 0.0,
    val scanResults: List<ScanResult> = emptyList()
)

/**
 * Servizio di wardriving che orchestra le scansioni Wi-Fi.
 *
 * Gestisce internamente l'intero ciclo di vita di una sessione:
 * 1. Crea la sessione nel database
 * 2. Avvia il GPS
 * 3. Esegue scansioni Wi-Fi in loop, salvando ogni rete con la posizione GPS
 * 4. Chiude e aggiorna la sessione alla cancellazione della coroutine
 */
interface WarDrivingService {
    /**
     * Avvia una sessione completa di wardriving e la esegue fino alla cancellazione.
     * La sessione viene creata all'avvio e chiusa automaticamente nel blocco finally,
     * garantendo la coerenza anche in caso di errori.
     *
     * @param onResult callback invocata dopo ogni ciclo di scansione completato
     * @throws Exception se il GPS non è disponibile entro il timeout iniziale
     */
    suspend fun runSession(onResult: (WarDrivingScanResult) -> Unit)
}
