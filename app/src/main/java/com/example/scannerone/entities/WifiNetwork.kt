package com.example.scannerone.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//rappresenta il router fisico scansionato
@Entity(
    tableName = "wifi_networks",
    indices = [Index(value = ["bssid"], unique = true)]
)
data class WifiNetwork(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bssid: String,
    val ssid: String,
    val capabilities: String,
    val frequency: Int,
    val realLatitude: Double? = null,
    val realLongitude: Double? = null,
    val estAccuracy: Float? = null,
    val realStreet: String? = null,
    val realCity: String? = null,
    val realRegion: String? = null,
    val realCountry: String? = null,
    val category: String = "OTHER",
    val security: String = "OTHER",
    val frequencyBand: Float = 0.0f
)
