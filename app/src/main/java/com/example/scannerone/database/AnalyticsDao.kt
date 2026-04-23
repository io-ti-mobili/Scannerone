package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Query
import com.example.scannerone.entities.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Query("SELECT COUNT(*) FROM wifi_networks")
    fun getTotalNetworksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM wifi_scan_records")
    fun getTotalScansCount(): Flow<Int>

    @Query("SELECT SUM(distanceMetres) FROM scan_sessions")
    fun getTotalDistance(): Flow<Double?>

    @Query("SELECT SUM(endTime - startTime) FROM scan_sessions WHERE endTime IS NOT NULL")
    fun getTotalTime(): Flow<Long?>

    @Query(
        """
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
        """
    )
    fun getDiscoveryTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query(
        """
        SELECT
            CAST((timestamp - :startTime) / :bucketSize AS INTEGER) as bucketIndex,
            COUNT(id) as count
        FROM wifi_scan_records
        WHERE timestamp >= :startTime AND timestamp < :endTime
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
        """
    )
    fun getScanTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query(
        """
        SELECT
            CAST((startTime - :startTime) / :bucketSize AS INTEGER) as bucketIndex,
            COUNT(id) as count
        FROM scan_sessions
        WHERE startTime >= :startTime AND startTime < :endTime
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
        """
    )
    fun getSessionTrendStats(startTime: Long, endTime: Long, bucketSize: Long): Flow<List<BucketStat>>

    @Query("SELECT * FROM scan_sessions WHERE endTime IS NOT NULL ORDER BY (endTime - startTime) DESC LIMIT 1")
    fun getLongestSession(): Flow<ScanSession?>

    @Query("SELECT * FROM scan_sessions ORDER BY distanceMetres DESC LIMIT 1")
    fun getMostDistanceSession(): Flow<ScanSession?>

    @Query(
        """
        SELECT s.* FROM scan_sessions s
        INNER JOIN wifi_scan_records r ON s.id = r.sessionId
        WHERE r.isFirstDiscovery = 1
        GROUP BY s.id
        ORDER BY COUNT(r.id) DESC
        LIMIT 1
        """
    )
    fun getSessionWithMostUniqueNetworks(): Flow<ScanSession?>

    @Query(
        """
        SELECT w.category as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.category
        """
    )
    fun getCategoryStatsFlow(sessionId: Int?): Flow<List<StatCount>>

    @Query(
        """
        SELECT w.security as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.security
        """
    )
    fun getSecurityStatsFlow(sessionId: Int?): Flow<List<StatCount>>

    @Query(
        """
        SELECT w.frequencyBand as type, COUNT(DISTINCT w.id) as count
        FROM wifi_networks w
        INNER JOIN wifi_scan_records r ON w.id = r.networkId
        WHERE (:sessionId IS NULL OR r.sessionId = :sessionId)
        GROUP BY w.frequencyBand
        """
    )
    fun getFrequencyStatsFlow(sessionId: Int?): Flow<List<StatCountFloat>>
}
