package com.example.scannerone.Services.ScanService

import android.net.wifi.ScanResult

interface ScanService {
    /**
     * Esegue una scansione delle reti Wi-Fi e restituisce la lista dei risultati.
     * @return Una lista di [ScanResult] contenente SSID, BSSID, RSSI, capacità, frequenza, ecc.
     */
    suspend fun scan(): List<ScanResult>
    fun isWifiEnabled(): Boolean
}
