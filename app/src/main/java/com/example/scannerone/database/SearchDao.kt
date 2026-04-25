package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Query
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query(
        """
        SELECT * FROM wifi_networks
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR security = :security)
        ORDER BY id DESC LIMIT 500
        """
    )
    fun searchNetworksAdvanced(
        ssid: String,
        bssid: String,
        address: String,
        security: String
    ): Flow<List<WifiNetwork>>

    @Query(
        """
        SELECT * FROM wifi_networks
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR security = :security)
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchNetworksAdvancedPaged(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        limit: Int,
        offset: Int
    ): Flow<List<WifiNetwork>>

    @Query(
        """
        SELECT COUNT(*) FROM wifi_networks
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR security = :security)
        """
    )
    fun countNetworksAdvancedFiltered(
        ssid: String,
        bssid: String,
        address: String,
        security: String
    ): Flow<Int>

    @Query(
        """
        SELECT id FROM wifi_networks
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR security = :security)
        ORDER BY id DESC
        LIMIT 1 OFFSET :offset
        """
    )
    suspend fun getNetworkIdAtFilteredOffset(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        offset: Int
    ): Int?
    /**
     * Returns the top [limit] scan records for [networkId], ordered descending by a composite
     * weight that combines accuracy (80%) and recency (20%).
     *
     * accuracyScore  = 1 - (scanAccuracy - MIN) / NULLIF(MAX - MIN, 0)   → lower accuracy = higher score
     * recencyScore   = (timestamp  - MIN) / NULLIF(MAX - MIN, 0)          → newer = higher score
     * weight         = 0.8 * accuracyScore + 0.2 * recencyScore
     *
     * When all values are equal the NULLIF guard yields NULL (treated as 0 by SQLite arithmetic),
     * so every row gets weight = 0 and ordering is stable/arbitrary — which is fine because all
     * records are equivalent in that degenerate case.
     */
    @Query("""
        SELECT * FROM wifi_scan_records
        WHERE networkId = :networkId
        ORDER BY (
            0.8 * (1.0 - (
                CAST(scanAccuracy AS REAL)
                - (SELECT MIN(scanAccuracy) FROM wifi_scan_records WHERE networkId = :networkId)
            ) / NULLIF(
                CAST(
                    (SELECT MAX(scanAccuracy) FROM wifi_scan_records WHERE networkId = :networkId)
                    - (SELECT MIN(scanAccuracy) FROM wifi_scan_records WHERE networkId = :networkId)
                AS REAL), 0.0)
            )
            +
            0.2 * (
                CAST(
                    timestamp - (SELECT MIN(timestamp) FROM wifi_scan_records WHERE networkId = :networkId)
                AS REAL)
                / NULLIF(
                    CAST(
                        (SELECT MAX(timestamp) FROM wifi_scan_records WHERE networkId = :networkId)
                        - (SELECT MIN(timestamp) FROM wifi_scan_records WHERE networkId = :networkId)
                    AS REAL), 0.0)
            )
        ) DESC
        LIMIT :limit
    """)
    suspend fun getBestScansForNetwork(networkId: Int, limit: Int): List<WifiScanRecord>
}
