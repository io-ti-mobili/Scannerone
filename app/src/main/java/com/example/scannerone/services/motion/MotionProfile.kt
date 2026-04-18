package com.example.scannerone.services.motion

/**
 * Parametri operativi associati a uno stato di movimento.
 */
data class MotionProfile(
    val gpsRateMs: Long,
    val scanTriggerDistanceM: Double,
    val stationaryScanIntervalMs: Long,
    val stillTimeoutMs: Long
)
