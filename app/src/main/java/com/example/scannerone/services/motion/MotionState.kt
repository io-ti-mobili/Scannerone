package com.example.scannerone.services.motion

sealed class MotionState {
    object Still     : MotionState()
    object Walking   : MotionState()
    object InVehicle : MotionState()

    /** Descrizione leggibile per i log. */
    override fun toString() = when (this) {
        is Still     -> "STILL"
        is Walking   -> "WALKING"
        is InVehicle -> "IN_VEHICLE"
    }
}
