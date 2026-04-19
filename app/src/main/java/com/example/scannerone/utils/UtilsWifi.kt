package com.example.scannerone.utils

enum class NetworkCategory(val color: androidx.compose.ui.graphics.Color) {
    ISP(androidx.compose.ui.graphics.Color(0xFF2196F3)),      // Blu
    FAST_FOOD(androidx.compose.ui.graphics.Color(0xFFFF9800)), // Arancione
    UNIVERSITY(androidx.compose.ui.graphics.Color(0xFF9C27B0)),// Viola
    HOTSPOT(androidx.compose.ui.graphics.Color(0xFFF44336)),   // Rosso
    OTHER(androidx.compose.ui.graphics.Color(0xFF9E9E9E))      // Grigio
}

fun categorizeNetwork(ssid: String): NetworkCategory {
    val lowerSsid = ssid.lowercase()
    return when {
        lowerSsid.contains("vodafone") || lowerSsid.contains("tim") ||
                lowerSsid.contains("fastweb") || lowerSsid.contains("wind") ||
                lowerSsid.contains("iliad") || lowerSsid.contains("sky") -> NetworkCategory.ISP

        lowerSsid.contains("mcdonald") || lowerSsid.contains("burger king") ||
                lowerSsid.contains("kfc") || lowerSsid.contains("starbucks") -> NetworkCategory.FAST_FOOD

        lowerSsid.contains("eduroam") || lowerSsid.contains("unimi") ||
                lowerSsid.contains("polimi") || lowerSsid.contains("studenti") -> NetworkCategory.UNIVERSITY

        lowerSsid.contains("iphone") || lowerSsid.contains("android") ||
                lowerSsid.contains("galaxy") || lowerSsid.contains("hotspot") -> NetworkCategory.HOTSPOT

        else -> NetworkCategory.OTHER
    }
}