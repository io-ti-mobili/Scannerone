package com.example.scannerone.services.motion

import com.example.scannerone.services.GPSService.Position

/**
 * Logica centralizzata di rilevamento dello stato di movimento.
 */
object MotionStateResolver {

    fun resolve(position: Position, prevPosition: Position?): MotionState {
        val speed = if (position.hasSpeed) {
            position.speed
        } else {
            estimateSpeed(position, prevPosition)
        }
        return fromSpeed(speed)
    }

    fun fromSpeed(speedMs: Float): MotionState = when {
        speedMs < MotionConfig.SPEED_STILL_MAX   -> MotionState.Still
        speedMs < MotionConfig.SPEED_VEHICLE_MIN -> MotionState.Walking
        else                                      -> MotionState.InVehicle
    }

    private fun estimateSpeed(current: Position, prev: Position?): Float {
        prev ?: return 0f
        val distM = prev.distanceTo(current)
        val dtMs = current.timestamp - prev.timestamp
        if (dtMs <= 0L) return 0f
        val speedMs = distM / (dtMs / 1_000f)
        return if (distM < MotionConfig.FALLBACK_DIST_M) 0f else speedMs
    }
}
