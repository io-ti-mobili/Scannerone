package com.example.scannerone.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMetres: Double = 0.0,
    val uniqueNetworksSeen: Int = 0  
)
