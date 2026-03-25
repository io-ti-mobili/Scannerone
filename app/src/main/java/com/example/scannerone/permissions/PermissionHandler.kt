package com.example.scannerone.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Controlla se UN singolo permesso è già stato concesso
fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

// Controlla se TUTTI i permessi richiesti sono stati concessi
fun Context.areAllPermissionsGranted(permissions: List<String>): Boolean =
    permissions.all { isPermissionGranted(it) }

// Composable riutilizzabile che espone stato + launcher
@Composable
fun rememberWifiPermissionState(
    permissions: List<String> = WifiPermissions.required,
    onGranted: () -> Unit = {},
    onDenied: (denied: List<String>) -> Unit = {}
): WifiPermissionState {
    val context = LocalContext.current

    var allGranted by remember {
        mutableStateOf(context.areAllPermissionsGranted(permissions))
    }
    var deniedPermissions by remember { mutableStateOf<List<String>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys.toList()
        deniedPermissions = denied
        allGranted = denied.isEmpty()
        if (denied.isEmpty()) onGranted() else onDenied(denied)
    }

    return remember(allGranted, deniedPermissions) {
        WifiPermissionState(
            allGranted = allGranted,
            deniedPermissions = deniedPermissions,
            requestPermissions = { launcher.launch(permissions.toTypedArray()) }
        )
    }
}

// Stato esposto al composable chiamante
data class WifiPermissionState(
    val allGranted: Boolean,
    val deniedPermissions: List<String>,
    val requestPermissions: () -> Unit
)