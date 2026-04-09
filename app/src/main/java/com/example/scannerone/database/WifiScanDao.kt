package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.entities.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiScanDao {
    @Query("SELECT * FROM wifi_networks")
    fun getAllNetworks(): Flow<List<WifiNetwork>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNetwork(network: WifiNetwork): Long

    @Query("SELECT id FROM wifi_networks WHERE bssid = :bssid")
    suspend fun getNetworkIdByBssid(bssid: String): Int?

    @Insert
    suspend fun insertScanRecord(record: WifiScanRecord)

    @Query("SELECT * FROM wifi_scan_records WHERE networkId = :networkId")
    suspend fun getScansForNetwork(networkId: Int): List<WifiScanRecord>

    @Query("UPDATE wifi_networks SET realLatitude = :lat, realLongitude = :lon, estAccuracy = :acc WHERE id = :networkId")
    suspend fun updateNetworkLocation(networkId: Int, lat: Double, lon: Double, acc: Float)

    @Query("SELECT * FROM wifi_networks")
    suspend fun getAllNetworksList(): List<WifiNetwork>

    @Query("SELECT id FROM wifi_networks")
    suspend fun getAllNetworkIds(): List<Int>

    @Query("SELECT COUNT(*) FROM wifi_scan_records WHERE networkId = :networkId")
    suspend fun getScanCountForNetwork(networkId: Int): Int

    @Query("UPDATE wifi_networks SET realStreet = :street, realCity = :city, realRegion = :region, realCountry = :country WHERE id = :networkId")
    suspend fun updateNetworkAddressDetails(networkId: Int, street: String?, city: String?, region: String?, country: String?)

    @Insert
    suspend fun insertSession(session: ScanSession): Long

    @Update
    suspend fun updateSession(session: ScanSession)

    @Query("SELECT COUNT(DISTINCT networkId) FROM wifi_scan_records WHERE sessionId = :sessionId")
    suspend fun getNetworksFoundInSession(sessionId: Int): Int

    @Query("SELECT COUNT(*) FROM wifi_scan_records WHERE sessionId = :sessionId")
    suspend fun getScansCompletedInSession(sessionId: Int): Int

    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScanSession>>


    @Query("""
    SELECT * FROM wifi_networks 
    WHERE realLatitude BETWEEN :south AND :north 
    AND realLongitude BETWEEN :west AND :east
""")
    suspend fun getNetworksInBoundingBox(north: Double, south: Double, east: Double, west: Double): List<WifiNetwork>
}
