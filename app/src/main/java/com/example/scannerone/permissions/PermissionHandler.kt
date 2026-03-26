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

// Composable GENERICO per qualsiasi lista di permessi
@Composable
fun rememberPermissionState(
    permissions: List<String>,
    onGranted: () -> Unit = {},
    onDenied: (denied: List<String>) -> Unit = {}
): PermissionState {
    val context = LocalContext.current

    var allGranted by remember(permissions) {
        mutableStateOf(context.areAllPermissionsGranted(permissions))
    }
    var deniedPermissions by remember(permissions) { mutableStateOf<List<String>>(emptyList()) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys.toList()
        deniedPermissions = denied
        allGranted = denied.isEmpty()
        if (denied.isEmpty()) {
            onGranted()
            pendingAction?.invoke()
            pendingAction = null
        } else {
            onDenied(denied)
            pendingAction = null
        }
    }

    return remember(allGranted, deniedPermissions, permissions) {
        PermissionState(
            allGranted = allGranted,
            deniedPermissions = deniedPermissions,
            requestPermissions = { launcher.launch(permissions.toTypedArray()) },
            runWithPermission = { action ->
                if (context.areAllPermissionsGranted(permissions)) {
                    action()
                } else {
                    pendingAction = action
                    launcher.launch(permissions.toTypedArray())
                }
            }
        )
    }
}

// Stato generico utilizzabile da qualsiasi feature
data class PermissionState(
    val allGranted: Boolean,
    val deniedPermissions: List<String>,
    val requestPermissions: () -> Unit,
    val runWithPermission: (onGranted: () -> Unit) -> Unit
)