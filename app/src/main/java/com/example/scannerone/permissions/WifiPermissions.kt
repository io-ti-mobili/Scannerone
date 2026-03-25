package com.example.scannerone.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

object WifiPermissions {
    val required: List<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        // Su Android 13+ serve anche NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
}

// Wrapper specifico per il Wi-Fi
// Se un domani cambi i permessi del Wi-Fi, modifichi solo questo file.
@androidx.compose.runtime.Composable
fun rememberWifiPermissionState(
    onGranted: () -> Unit = {},
    onDenied: (denied: List<String>) -> Unit = {}
): PermissionState = rememberPermissionState(
    permissions = WifiPermissions.required,
    onGranted = onGranted,
    onDenied = onDenied
)