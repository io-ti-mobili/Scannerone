package com.example.scannerone.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_scans")
data class WifiScan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)