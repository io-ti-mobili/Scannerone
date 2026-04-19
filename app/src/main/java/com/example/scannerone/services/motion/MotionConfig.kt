package com.example.scannerone.services.motion

/**
 * Soglie di rilevamento e profili operativi per ogni stato.
 */
object MotionConfig {

    /** Sotto questa soglia → Still (≈ 1.8 km/h) */
    const val SPEED_STILL_MAX = 0.5f

    /** Sopra questa soglia → InVehicle (≈ 10 km/h) */
    const val SPEED_VEHICLE_MIN = 2.8f

    /** Soglia distanza fallback quando il chip GPS non fornisce speed. */
    const val FALLBACK_DIST_M = 2.5f

    val PROFILE_STILL = MotionProfile(
        gpsRateMs                = 3_000L,
        scanTriggerDistanceM     = Double.MAX_VALUE,
        stationaryScanIntervalMs = 30_000L,
        stillTimeoutMs           = 0L
    )

    val PROFILE_WALKING = MotionProfile(
        gpsRateMs                = 500L,
        scanTriggerDistanceM     = 10.0,
        stationaryScanIntervalMs = 30_000L,
        stillTimeoutMs           = 15_000L
    )

    val PROFILE_IN_VEHICLE = MotionProfile(
        gpsRateMs                = 500L,
        scanTriggerDistanceM     = 10.0,
        stationaryScanIntervalMs = 30_000L,
        stillTimeoutMs           = 15_000L
    )

    /** Restituisce il profilo associato allo stato corrente. */
    fun profileFor(state: MotionState): MotionProfile = when (state) {
        is MotionState.Still     -> PROFILE_STILL
        is MotionState.Walking   -> PROFILE_WALKING
        is MotionState.InVehicle -> PROFILE_IN_VEHICLE
    }
}
