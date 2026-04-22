package com.example.scannerone.ui

import com.example.scannerone.R

/**
 * Destinazioni di navigazione dell'app.
 * Aggiungi qui nuove schermate.
 */
enum class AppDestination(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    DATABASESCREEN("Database", R.drawable.outline_database_24),
    WIFISCAN("WifiScan", R.drawable.outline_android_wifi_3_bar_24),
    MAP("Map", R.drawable.outline_map_24),
    RIEPILOGO("Riepilogo", R.drawable.outline_edit_document_24),
    SETTINGS("Settings", R.drawable.outline_build_24)

}