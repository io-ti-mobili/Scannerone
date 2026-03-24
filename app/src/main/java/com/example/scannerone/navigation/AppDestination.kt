package com.example.scannerone.navigation

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
    TEST("Test", R.drawable.ic_home),
    DATABASESCREEN("Database", R.drawable.ic_home)
}
