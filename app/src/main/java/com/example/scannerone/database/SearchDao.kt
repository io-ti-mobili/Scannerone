package com.example.scannerone.database

import androidx.room.Dao
import androidx.room.Query
import com.example.scannerone.entities.WifiNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query(
        """
        SELECT * FROM wifi_networks
        WHERE (:ssid = '' OR ssid LIKE '%' || :ssid || '%')
        AND (:bssid = '' OR bssid LIKE '%' || :bssid || '%')
        AND (:address = '' OR realCity LIKE '%' || :address || '%' OR realStreet LIKE '%' || :address || '%' OR realRegion LIKE '%' || :address || '%')
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
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
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
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
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
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
        AND (:security = 'Tutte' OR capabilities LIKE '%' || :security || '%')
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
}
