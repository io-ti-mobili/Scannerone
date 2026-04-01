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
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
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

        val results = withTimeoutOrNull(WarDrivingConfig.WIFI_SCAN_TIMEOUT_MS) {
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

                awaitClose { context.unregisterReceiver(receiver) }
            }.first()
        }

        return results ?: run {
            Log.w("WifiScanServiceImpl", "Timeout broadcast dopo ${WarDrivingConfig.WIFI_SCAN_TIMEOUT_MS}ms, uso cache")
            if (ActivityCompat.checkSelfPermission(
                    this.context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("WifiScanServiceImpl", "Permesso ACCESS_FINE_LOCATION mancante")
                return emptyList()
            }
            wifiManager.scanResults ?: emptyList()
        }
    }
}