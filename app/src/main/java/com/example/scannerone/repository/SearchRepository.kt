package com.example.scannerone.repository

import com.example.scannerone.database.SearchDao
import com.example.scannerone.entities.WifiNetwork
import kotlinx.coroutines.flow.Flow

class SearchRepository(private val searchDao: SearchDao) {

    fun searchNetworksAdvancedPaged(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        limit: Int,
        offset: Int
    ): Flow<List<WifiNetwork>> =
        searchDao.searchNetworksAdvancedPaged(ssid, bssid, address, security, limit, offset)

    fun countNetworksAdvancedFiltered(
        ssid: String,
        bssid: String,
        address: String,
        security: String
    ): Flow<Int> = searchDao.countNetworksAdvancedFiltered(ssid, bssid, address, security)

    suspend fun hasFilteredNetworkAtOffset(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        offset: Int
    ): Boolean = searchDao.getNetworkIdAtFilteredOffset(ssid, bssid, address, security, offset) != null
}
