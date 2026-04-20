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
    DATABASESCREEN("Database", R.drawable.database),
    WIFISCAN("WifiScan", R.drawable.wifi),
    MAP("Map", R.drawable.map),
    RIEPILOGO("Riepilogo", R.drawable.report),
    SETTINGS("Settings", R.drawable.settings)
   
}