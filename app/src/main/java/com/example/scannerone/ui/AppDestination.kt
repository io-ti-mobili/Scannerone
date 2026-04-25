package com.example.scannerone.ui

import androidx.annotation.StringRes
import com.example.scannerone.R

/**
 * Destinazioni di navigazione dell'app.
 * Aggiungi qui nuove schermate.
 */
enum class AppDestination(
    @StringRes val labelRes: Int,
    val icon: Int,
) {
    HOME(R.string.destination_home, R.drawable.ic_home),
    DATABASESCREEN(R.string.destination_database, R.drawable.outline_database_24),
    WIFISCAN(R.string.destination_wifi_scan, R.drawable.outline_android_wifi_3_bar_24),
    MAP(R.string.destination_map, R.drawable.outline_map_24),
    RIEPILOGO(R.string.destination_summary, R.drawable.outline_bar_chart_24),
    SETTINGS(R.string.destination_settings, R.drawable.outline_build_24)

}