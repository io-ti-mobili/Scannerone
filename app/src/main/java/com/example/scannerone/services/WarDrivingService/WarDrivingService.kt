package com.example.scannerone.services.WarDrivingService

import com.example.scannerone.services.GPSService.Position

/**
 * Risultato di un ciclo di scansione wardriving.
 */
data class WarDrivingScanResult(
    val networksFound: Int,
    val networksSaved: Int,
    val position: Position
)

/**
 * Servizio di wardriving che orchestra le scansioni Wi-Fi.
 *
 * Un ciclo completo consiste in:
 * 1. Ottenere la posizione GPS corrente
 * 2. Scansionare le reti Wi-Fi visibili
 * 3. Salvare ogni rete trovata nel database, associata alla posizione
 */
interface WarDrivingService {
    /**
     * Esegue un ciclo completo di wardriving.
     * @return [WarDrivingScanResult] con il numero di reti trovate/salvate e la posizione
     * @throws Exception se il GPS non è disponibile o la scansione Wi-Fi fallisce
     */
    suspend fun performScan(sessionId: Int? = null): WarDrivingScanResult
}
