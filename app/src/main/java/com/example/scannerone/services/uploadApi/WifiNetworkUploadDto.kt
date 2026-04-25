package com.example.scannerone.services.uploadApi

data class WifiNetworkUploadDto(
    val bssid: String,
    val ssid: String,
    val frequency: Int,
    val realLatitude: Double?,
    val realLongitude: Double?,
    val estAccuracy: Float?,
    val category: String = "OTHER",
    val security: String = "OTHER",
    val frequencyBand: Float = 0.0f
)
