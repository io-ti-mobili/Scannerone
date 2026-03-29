package com.example.scannerone.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Stato dei permessi. Usalo così:
 *
 * ```
 * val permissions = rememberPermissionState(PermissionGroup.WIFI, PermissionGroup.LOCATION)
 *
 * Button(onClick = {
 *     permissions.runWithPermission { startScan() }
 * })
 * ```
 *
 * Se i permessi sono già concessi → esegue subito.
 * Se mancano → li chiede → se l'utente accetta, esegue. Se rifiuta, non fa niente.
 */
data class PermissionState(
    val allGranted: Boolean,
    val requestPermissions: () -> Unit,
    val runWithPermission: (action: () -> Unit) -> Unit
)

@Composable
fun rememberPermissionState(vararg groups: PermissionGroup): PermissionState {
    val context = LocalContext.current

    val allPermissions = remember(*groups) {
        groups.flatMap { it.permissions }.distinct()
    }

    var allGranted by remember {
        mutableStateOf(allPermissions.all { context.isGranted(it) })
    }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        allGranted = granted
        if (granted) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }

    return remember(allGranted, allPermissions) {
        PermissionState(
            allGranted = allGranted,
            requestPermissions = { launcher.launch(allPermissions.toTypedArray()) },
            runWithPermission = { action ->
                if (allPermissions.all { context.isGranted(it) }) {
                    action()
                } else {
                    pendingAction = action
                    launcher.launch(allPermissions.toTypedArray())
                }
            }
        )
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
