package com.example.scannerone.permissions

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable

object SensorPermissions {
    val required: List<String> = buildList {
        // Esempio: su Android 12+ (S) serve questo permesso per alta frequenza
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
        }
    }
}

// Composable specifico per i sensori
// Dimostra che è completamente separato dal Wi-Fi
@Composable
fun rememberSensorPermissionState(
    onGranted: () -> Unit = {},
    onDenied: (denied: List<String>) -> Unit = {}
): PermissionState = rememberPermissionState(
    permissions = SensorPermissions.required,
    onGranted = onGranted,
    onDenied = onDenied
)
