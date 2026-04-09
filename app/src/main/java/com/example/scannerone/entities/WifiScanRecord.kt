package com.example.scannerone.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//rappresenta la storia di quante volte un utente ha scansionato una certa rete
@Entity(
    tableName = "wifi_scan_records",
    foreignKeys = [
        ForeignKey(
            entity = WifiNetwork::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("networkId"), Index("sessionId")]
)
data class WifiScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val networkId: Int,
    val sessionId: Int? = null,
    val timestamp: Long,
    val rssi: Int,
    val scanLatitude: Double,
    val scanLongitude: Double,
    val scanAccuracy: Float
)
