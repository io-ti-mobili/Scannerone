package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.entities.ScanSession
import kotlinx.coroutines.flow.Flow

data class StatCount(
    val type: String?,
    val count: Int
)

data class StatCountFloat(
    val type: Float?,
    val count: Int
)

data class BucketStat(
    val bucketIndex: Int,
    val count: Int
)

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

    @Query("SELECT * FROM wifi_networks LIMIT :limit OFFSET :offset")
    fun getNetworksPaged(limit: Int, offset: Int): List<WifiNetwork>

    @Query("SELECT * FROM scan_sessions LIMIT :limit OFFSET :offset")
    fun getSessionsPaged(limit: Int, offset: Int): List<ScanSession>

    @Query("SELECT * FROM wifi_scan_records LIMIT :limit OFFSET :offset")
    fun getRecordsPaged(limit: Int, offset: Int): List<WifiScanRecord>

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

    @Query("SELECT COUNT(*) FROM wifi_networks")
    fun getTotalNetworksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM wifi_scan_records")
    fun getTotalScansCount(): Flow<Int>

    @Query("SELECT SUM(distanceMetres) FROM scan_sessions")
    fun getTotalDistance(): Flow<Double?>

    @Query("SELECT SUM(endTime - startTime) FROM scan_sessions WHERE endTime IS NOT NULL")
    fun getTotalTime(): Flow<Long?>

    @Query("""
        SELECT 
            CAST((min_ts - :startTime) / :bucketSize AS INTEGER) as bucketIndex,
            COUNT(networkId) as count
        FROM (
            SELECT networkId, MIN(timestamp) as min_ts 
            FROM wifi_scan_records 
            GROUP BY networkId
        )
        WHERE min_ts >= :startTime AND min_ts < :endTime
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
    """)
    fun getDiscoveryTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query("""
        SELECT 
            CAST((timestamp - :startTime) / :bucketSize AS INTEGER) as bucketIndex,
            COUNT(id) as count
        FROM wifi_scan_records
        WHERE timestamp >= :startTime AND timestamp < :endTime
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
    """)
    fun getScanTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query("""
        SELECT 
            CAST((startTime - :startTime) / :bucketSize AS INTEGER) as bucketIndex,
            COUNT(id) as count
        FROM scan_sessions
        WHERE startTime >= :startTime AND startTime < :endTime
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
    """)
    fun getSessionTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query("SELECT * FROM scan_sessions WHERE endTime IS NOT NULL ORDER BY (endTime - startTime) DESC LIMIT 1")
    fun getLongestSession(): Flow<ScanSession?>

    @Query("SELECT * FROM scan_sessions ORDER BY distanceMetres DESC LIMIT 1")
    fun getMostDistanceSession(): Flow<ScanSession?>

    @Query("""
        SELECT s.* FROM scan_sessions s 
        INNER JOIN wifi_scan_records r ON s.id = r.sessionId 
        WHERE r.isFirstDiscovery = 1 
        GROUP BY s.id 
        ORDER BY COUNT(r.id) DESC 
        LIMIT 1
    """)
    fun getSessionWithMostUniqueNetworks(): Flow<ScanSession?>

    @Query("SELECT * FROM wifi_scan_records ORDER BY timestamp DESC LIMIT 500")
    fun getLastScans(): Flow<List<WifiScanRecord>>

    @Query("SELECT COUNT(id) FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionTotalScansCountFlow(sessionId: Int?): Flow<Int>

    @Query("SELECT COUNT(id) FROM wifi_scan_records WHERE isFirstDiscovery = 1 AND (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionDiscoveryCountFlow(sessionId: Int?): Flow<Int>

    @Query("SELECT COUNT(DISTINCT networkId) FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId)")
    fun getSessionUniqueNetworksCountFlow(sessionId: Int?): Flow<Int>








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
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchNetworksAdvancedPaged(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        limit: Int,
        offset: Int
    ): Flow<List<WifiNetwork>>

    @Query("""
        SELECT COUNT(*) FROM wifi_networks 
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
    """)
    fun countNetworksAdvancedFiltered(
        ssid: String,
        bssid: String,
        address: String,
        security: String
    ): Flow<Int>

    @Query("""
        SELECT id FROM wifi_networks 
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
        ORDER BY id DESC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getNetworkIdAtFilteredOffset(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        offset: Int
    ): Int?

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

    // ---- Export/Import ----

    @Query("DELETE FROM wifi_scan_records")
    suspend fun deleteAllRecords()

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM wifi_networks")
    suspend fun deleteAllNetworks()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<WifiScanRecord>)
    // 1. Ottiene tutte le sessioni dal DB per il menu a tendina
    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScanSession>>

    @Query("UPDATE scan_sessions SET distanceMetres = :distance WHERE id = :sessionId")
    suspend fun updateSessionDistance(sessionId: Int, distance: Double)



    @Query("SELECT * FROM wifi_scan_records WHERE (:sessionId IS NULL OR sessionId = :sessionId) ORDER BY timestamp ASC")
    fun getScanRecordsForSession(sessionId: Int?): kotlinx.coroutines.flow.Flow<List<com.example.scannerone.entities.WifiScanRecord>>
    @Delete
    suspend fun deleteNetwork(network: WifiNetwork)

    @Query("""
        SELECT w.category as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.category
    """)
    fun getCategoryStatsFlow(sessionId: Int?): Flow<List<StatCount>>

    @Query("""
        SELECT w.security as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.security
    """)
    fun getSecurityStatsFlow(sessionId: Int?): Flow<List<StatCount>>

    @Query("""
        SELECT w.frequencyBand as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.frequencyBand
    """)
    fun getFrequencyStatsFlow(sessionId: Int?): Flow<List<StatCountFloat>>
}

