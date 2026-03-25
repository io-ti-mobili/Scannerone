package com.example.scannerone.Services.ScanService

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class WifiScanServiceImpl(private val context: Context) : ScanService {

    override suspend fun scan(): List<ScanResult> {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: throw Exception("Servizio WifiManager non disponibile sul dispositivo")

        if (!wifiManager.isWifiEnabled) {
            throw Exception("Il Wi-Fi è disattivato. Attivalo e riprova.")
        }

        // Ascolta il broadcast di fine scansione tramite Flow
        val results = withTimeoutOrNull(10_000L) {
            callbackFlow {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val success = intent.getBooleanExtra(
                            WifiManager.EXTRA_RESULTS_UPDATED, false
                        )
                        Log.d("WifiScanServiceImpl", "Broadcast ricevuto, success=$success")
                        trySend(
                            try {
                                wifiManager.scanResults ?: emptyList()
                            } catch (e: SecurityException) {
                                throw Exception("Permessi mancanti: ${e.message}")
                            }
                        )
                        close()
                    }
                }

                context.registerReceiver(
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )

                @Suppress("DEPRECATION")
                val started = wifiManager.startScan()
                Log.d("WifiScanServiceImpl", "startScan() returned: $started")

                // startScan()=false non è fatale su Android 9+, il receiver
                // verrà comunque notificato con i risultati cached

                awaitClose { context.unregisterReceiver(receiver) }
            }.first()
        }

        return results
            ?: run {
                // Timeout: nessun broadcast ricevuto in 10s, ritorna cache
                Log.w("WifiScanServiceImpl", "Timeout broadcast, uso scanResults cached")
                if (ActivityCompat.checkSelfPermission(
                        this.context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    Log.e("AAAAAAAAAAAAAAAAAAA", "AAAAAAAA")
                    return emptyList()
                }
                wifiManager.scanResults ?: emptyList()
            }
    }
}