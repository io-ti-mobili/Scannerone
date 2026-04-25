package com.example.scannerone.viewmodel

/**
 * Modelli di dominio condivisi tra i ViewModel del package.
 */

enum class StrategyType { CENTROID, TRILATERATION }

data class StrategyConfig(
    val baseStrategyType: StrategyType = StrategyType.CENTROID,
    val useRansac: Boolean = false,
    val useGpsWeight: Boolean = false
)

data class SearchFilters(
    val ssid: String = "",
    val bssid: String = "",
    val address: String = "",
    val security: String = "Tutte"
)
