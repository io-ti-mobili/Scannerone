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

    @Query("SELECT * FROM wifi_networks WHERE ssid LIKE '%' || :searchQuery || '%' OR bssid LIKE '%' || :searchQuery || '%' ORDER BY id DESC LIMIT 500")
    suspend fun searchNetworks(searchQuery: String): List<WifiNetwork>

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

    @androidx.room.Delete
    suspend fun deleteNetwork(network: com.example.scannerone.entities.WifiNetwork)




    @Query("""
        SELECT * FROM wifi_networks 
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
        ORDER BY id DESC LIMIT 500
    """)
    fun searchNetworksAdvanced(ssid: String, bssid: String, address: String, security: String): kotlinx.coroutines.flow.Flow<List<WifiNetwork>>

    @Query("""
    SELECT * FROM wifi_networks 
    WHERE realLatitude BETWEEN :south AND :north 
    AND realLongitude BETWEEN :west AND :east
""")
    suspend fun getNetworksInBoundingBox(north: Double, south: Double, east: Double, west: Double): List<WifiNetwork>

    @Query("UPDATE wifi_networks SET ssid = :ssid WHERE bssid = :bssid")
    suspend fun updateNetworkSsid(bssid: String, ssid: String)

    @Query("DELETE FROM wifi_scan_records WHERE id IN (:ids)")
    suspend fun deleteScanRecordsByIds(ids: List<Int>)
}
