package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.scannerone.entities.WifiScan
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiScanDao {
    @Query("SELECT * FROM wifi_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<WifiScan>>

    @Insert
    suspend fun insertScan(scan: WifiScan)
}
