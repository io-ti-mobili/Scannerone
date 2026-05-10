package com.example.scannerone.services.motion

/**
 * Soglie di rilevamento e profili operativi per ogni stato.
 */
object MotionConfig {

    /** Sotto questa soglia → Still (≈ 1.8 km/h) */
    const val SPEED_STILL_MAX = 0.5f

    /** Sopra questa soglia → InVehicle (≈ 10 km/h) */
    const val SPEED_VEHICLE_MIN = 2.8f

    /**
     * Soglia distanza fallback quando il chip GPS non fornisce speed.
     * Impostata a 8m per essere superiore alla tipica accuracy GPS (±5–10m)
     * ed evitare falsi Walking causati da rumore del segnale.
     */
    const val FALLBACK_DIST_M = 8.0f

    // ── Isteresi per FusedMotionStateSource ──────────────────────────────

    /**
     * Numero di conferme AR/GPS consecutive richieste prima di passare
     * allo stato Still. Più alto = meno falsi positivi "mi fermo" durante
     * brevi rallentamenti (es. semaforo).
     */
    const val HYSTERESIS_STILL_COUNT = 3

    /**
     * Numero di conferme richieste per passare a Walking o InVehicle.
     * Intenzionalmente 1: reagire subito all'inizio del movimento è utile
     * per avviare la scansione tempestivamente.
     */
    const val HYSTERESIS_MOVE_COUNT = 1

    /**
     * Intervallo minimo (ms) tra due cambi di stato consecutivi.
     * Evita il flip-flop rapido tra stati adiacenti.
     */
    const val HYSTERESIS_DEBOUNCE_MS = 3_000L

    val PROFILE_STILL = MotionProfile(
        gpsRateMs                = 3_000L,
        scanIntervalMs           = 60_000L,
        stillTimeoutMs           = 0L
    )

    val PROFILE_WALKING = MotionProfile(
        gpsRateMs                = 500L,
        scanIntervalMs           = 30_000L,
        stillTimeoutMs           = 15_000L
    )

    val PROFILE_IN_VEHICLE = MotionProfile(
        gpsRateMs                = 500L,
        scanIntervalMs           = 10_000L,
        stillTimeoutMs           = 15_000L
    )

    /** Restituisce il profilo associato allo stato corrente. */
    fun profileFor(state: MotionState): MotionProfile = when (state) {
        is MotionState.Still     -> PROFILE_STILL
        is MotionState.Walking   -> PROFILE_WALKING
        is MotionState.InVehicle -> PROFILE_IN_VEHICLE
    }
}

