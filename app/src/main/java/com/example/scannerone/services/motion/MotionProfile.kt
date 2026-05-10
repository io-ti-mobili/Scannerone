package com.example.scannerone.services.motion

/**
 * Parametri operativi associati a uno stato di movimento.
 */
data class MotionProfile(
    val gpsRateMs: Long,
    val scanIntervalMs: Long,
    val stillTimeoutMs: Long
)
