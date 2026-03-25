package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
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
}
