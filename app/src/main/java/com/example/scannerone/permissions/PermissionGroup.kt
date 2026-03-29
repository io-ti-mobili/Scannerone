package com.example.scannerone.permissions

import android.Manifest
import android.os.Build

/**
 * Tutti i gruppi di permessi dell'app.
 * Per aggiungere un nuovo gruppo, aggiungi un entry qui.
 */
enum class PermissionGroup(val permissions: List<String>) {

    LOCATION(
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    ),

    WIFI(
        buildList {
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    ),

    SENSORS(
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
            }
        }
    );
}
