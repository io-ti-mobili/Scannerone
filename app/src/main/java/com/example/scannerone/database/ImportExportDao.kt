package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord

@Dao
interface ImportExportDao {
    @Query("SELECT * FROM wifi_networks LIMIT :limit OFFSET :offset")
    fun getNetworksPaged(limit: Int, offset: Int): List<WifiNetwork>

    @Query("SELECT * FROM scan_sessions LIMIT :limit OFFSET :offset")
    fun getSessionsPaged(limit: Int, offset: Int): List<ScanSession>

    @Query("SELECT * FROM wifi_scan_records LIMIT :limit OFFSET :offset")
    fun getRecordsPaged(limit: Int, offset: Int): List<WifiScanRecord>

    @Query("DELETE FROM wifi_scan_records")
    suspend fun deleteAllRecords()

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM wifi_networks")
    suspend fun deleteAllNetworks()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<WifiScanRecord>)
}
