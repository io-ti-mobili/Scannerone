package com.example.scannerone.services.ScanService

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.ScanResult
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.scannerone.Services.ScanService.WifiScanServiceImpl
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.LocationManagerGPSServiceImplV4
import com.example.scannerone.services.WarDrivingService.WarDrivingServiceImplV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WifiForegroundService : Service() {

    companion object {
        private const val TAG = "WifiForegroundService"
        private const val CHANNEL_ID = "scanner_channel"
        private const val NOTIFICATION_ID = 1

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _lastScanResults = MutableStateFlow<List<ScanResult>>(emptyList())
        val lastScanResults: StateFlow<List<ScanResult>> = _lastScanResults
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isScanning = false
    private var totalNetworksSaved = 0
    private var totalScansCompleted = 0

    override fun onCreate() {
        super.onCreate()
        creaCanaleNotifica()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifica = creaNotifica("Avvio scansione...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notifica)
        }

        _isRunning.value = true
        totalNetworksSaved = 0
        totalScansCompleted = 0

        iniziaScansioneInBackground()

        return START_STICKY
    }

    private fun iniziaScansioneInBackground() {
        if (isScanning) return
        isScanning = true

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.wifiScanDao()
            val repository = WifiScanRepository(dao)
            val scanService = WifiScanServiceImpl(applicationContext)
            val gpsService = LocationManagerGPSServiceImplV4(applicationContext)
            val warDrivingService = WarDrivingServiceImplV2(scanService, gpsService, repository, dao)


            // Attendi attivamente che i servizi siano abilitati
            while (!gpsService.isGpsEnabled() || !scanService.isWifiEnabled()) {
                val isGpsEnabled = gpsService.isGpsEnabled()
                val isWifiEnabled = scanService.isWifiEnabled()
                
                val errorMsg = when {
                    !isGpsEnabled && !isWifiEnabled -> "In attesa di Wi-Fi e GPS..."
                    !isGpsEnabled -> "In attesa del GPS..."
                    else -> "In attesa del Wi-Fi..."
                }
                aggiornaNotifica(errorMsg)
                Log.w(TAG, "Attesa attivazione servizi: $errorMsg")
                
                // Pausa prima di ricontrollare. 
                // Se l'utente clicca "Stop", la coroutine viene cancellata e questo delay si interrompe automaticamente.
                kotlinx.coroutines.delay(2000)
            }

            aggiornaNotifica("In attesa del primo fix GPS...")

            try {
                warDrivingService.runSession { result ->
                    totalScansCompleted++
                    totalNetworksSaved = result.uniqueNetworksInSession
                    _lastScanResults.value = result.scanResults

                    val distKm = result.totalDistanceMetres / 1000.0
                    Log.d(TAG, "Scan #$totalScansCompleted: ${result.networksSaved}/${result.networksFound} reti | " +
                            "Dist: ${String.format("%.2f", distKm)}km | GPS: ${result.position.getAge()}ms | Totale reti uniche: $totalNetworksSaved")

                    aggiornaNotifica(
                        String.format(java.util.Locale.getDefault(), "Dist: %.2f km | Reti: %d | Scan #%d",
                            distKm, totalNetworksSaved, totalScansCompleted)
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job.cancel() lancia questa eccezione per interrompere in modo sicuro la coroutine.
                // Non è un errore, ma la normale procedura di spegnimento. 
                Log.d(TAG, "Sessione wardriving fermata (coroutine annullata regolarmente).")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Errore sessione wardriving: ${e.message}", e)
                aggiornaNotifica("Errore: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servizio fermato. Totale scansioni: $totalScansCompleted, reti salvate: $totalNetworksSaved")
        serviceJob.cancel()
        isScanning = false
        _isRunning.value = false
        _lastScanResults.value = emptyList()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun creaCanaleNotifica() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scanner Wi-Fi Background",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun creaNotifica(testo: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scannerone — WarDriving")
            .setContentText(testo)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun aggiornaNotifica(testo: String) {
        val notification = creaNotifica(testo)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}