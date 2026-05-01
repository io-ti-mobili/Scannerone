package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Query("SELECT * FROM wifi_networks")
    fun getAllNetworks(): Flow<List<WifiNetwork>>

    @Query("SELECT * FROM wifi_networks")
    suspend fun getAllNetworksSync(): List<WifiNetwork>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNetwork(network: WifiNetwork): Long

    @Query("SELECT * FROM wifi_networks WHERE id = :networkId")
    suspend fun getNetworkById(networkId: Int): WifiNetwork?

    @Query("SELECT id FROM wifi_networks WHERE bssid = :bssid")
    suspend fun getNetworkIdByBssid(bssid: String): Int?

    @Insert
    suspend fun insertScanRecord(record: WifiScanRecord)

    @Query("SELECT * FROM wifi_scan_records WHERE networkId = :networkId")
    suspend fun getScansForNetwork(networkId: Int): List<WifiScanRecord>

    @Query("UPDATE wifi_networks SET realLatitude = :lat, realLongitude = :lon, estAccuracy = :acc WHERE id = :networkId")
    suspend fun updateNetworkLocation(networkId: Int, lat: Double, lon: Double, acc: Float)


    @Query("SELECT id FROM wifi_networks")
    suspend fun getAllNetworkIds(): List<Int>

    @Query("SELECT COUNT(*) FROM wifi_scan_records WHERE networkId = :networkId")
    suspend fun getScanCountForNetwork(networkId: Int): Int

    @Query("UPDATE wifi_networks SET realStreet = :street, realCity = :city, realRegion = :region, realCountry = :country WHERE id = :networkId")
    suspend fun updateNetworkAddressDetails(networkId: Int, street: String?, city: String?, region: String?, country: String?)

    @Query("UPDATE wifi_networks SET ssid = :ssid WHERE bssid = :bssid")
    suspend fun updateNetworkSsid(bssid: String, ssid: String)

    @Query("DELETE FROM wifi_scan_records WHERE id IN (:ids)")
    suspend fun deleteScanRecordsByIds(ids: List<Int>)

    @Delete
    suspend fun deleteNetwork(network: WifiNetwork)
}
