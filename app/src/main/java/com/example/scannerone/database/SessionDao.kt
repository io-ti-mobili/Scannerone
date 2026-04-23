package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: ScanSession): Long

    @Update
    suspend fun updateSession(session: ScanSession)

    @Query("SELECT COUNT(DISTINCT networkId) FROM wifi_scan_records WHERE sessionId = :sessionId")
    suspend fun getNetworksFoundInSession(sessionId: Int): Int

    @Query("SELECT COUNT(id) FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionTotalScansCountFlow(sessionId: Int?): Flow<Int>

    @Query("SELECT COUNT(id) FROM wifi_scan_records WHERE isFirstDiscovery = 1 AND (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionDiscoveryCountFlow(sessionId: Int?): Flow<Int>

    @Query("SELECT COUNT(DISTINCT networkId) FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionUniqueNetworksCountFlow(sessionId: Int?): Flow<Int>

    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScanSession>>

    @Query("UPDATE scan_sessions SET distanceMetres = :distance WHERE id = :sessionId")
    suspend fun updateSessionDistance(sessionId: Int, distance: Double)

    @Query("SELECT * FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId) ORDER BY timestamp ASC")
    fun getScanRecordsForSession(sessionId: Int?): Flow<List<WifiScanRecord>>
}
