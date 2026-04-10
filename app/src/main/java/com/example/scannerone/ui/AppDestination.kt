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
    DATABASESCREEN("Database", R.drawable.ic_home),
    WIFISCAN("Scanner Wifi", R.drawable.ic_home),
    MAP("Map", R.drawable.ic_home),
    RIEPILOGO("Riepilogo", R.drawable.ic_home)
}