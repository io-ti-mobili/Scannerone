package com.example.scannerone.services.GPSService

/**
 * Rappresenta una posizione geografica ottenuta dal dispositivo.
 */
data class Position(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

/**
 * Interfaccia per ottenere la posizione GPS del dispositivo.
 * L'implementazione concreta è disaccoppiata così da poter
 * sostituire il provider (LocationManager, FusedLocation, ecc.)
 * senza impatto sui consumatori.
 */
interface GPSService {
    /**
     * Ottiene la posizione corrente del dispositivo.
     * @return Un oggetto [Position] con latitudine, longitudine e accuratezza
     * @throws Exception se il GPS non è disponibile o non riesce a ottenere un fix
     */
    suspend fun getPosition(): Position
}
