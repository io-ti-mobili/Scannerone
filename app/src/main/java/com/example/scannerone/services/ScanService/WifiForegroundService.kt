package com.example.scannerone.services.ScanService

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.scannerone.Services.ScanService.WifiScanServiceImpl
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.LocationManagerGPSServiceImpl
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import com.example.scannerone.services.WarDrivingService.WarDrivingServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.scannerone.entities.ScanSession
import com.example.scannerone.services.GPSService.Position
import java.util.Locale

class WifiForegroundService : Service() {

    companion object {
        private const val TAG = "WifiForegroundService"
        private const val CHANNEL_ID = "scanner_channel"
        private const val NOTIFICATION_ID = 1

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isScanning = false
    private var totalNetworksSaved = 0
    private var totalScansCompleted = 0
    private var lastScanDurationMs = 0L
    private var lastGPSAgeMs = 0L
    
    private var totalDistanceMetres = 0.0
    private var lastPosition: Position? = null
    private var startTime = 0L
    private var currentSessionId: Int? = null

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
        startTime = System.currentTimeMillis()
        totalDistanceMetres = 0.0
        lastPosition = null
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
            val gpsService = LocationManagerGPSServiceImpl(applicationContext)
            val warDrivingService = WarDrivingServiceImpl(scanService, gpsService, repository)

            try {
                // Crea sessione iniziale
                val session = ScanSession(startTime = startTime)
                currentSessionId = dao.insertSession(session).toInt()
                Log.d(TAG, "Nuova sessione creata: ID=$currentSessionId")

                gpsService.startContinuousUpdates { position ->
                    // Calcolo distanza
                    lastPosition?.let { prev ->
                        val dist = prev.distanceTo(position)
                        if (position.accuracy < 50) {
                            totalDistanceMetres += dist
                        }
                    }
                    lastPosition = position
                    
                    warDrivingService.addGPSPosition(position)
                    Log.d(TAG, "GPS fix aggiunto al buffer: acc=${position.accuracy}m | Dist: ${String.format("%.2f", totalDistanceMetres)}m")
                }
                
                Log.d(TAG, "Wardriving avviato (GPS: ${WarDrivingConfig.GPS_UPDATE_INTERVAL_MS}ms, Scan: ${WarDrivingConfig.SCAN_INTERVAL_MS}ms)")

                Log.d(TAG, "Attesa primo fix GPS...")
                aggiornaNotifica("In attesa del primo fix GPS...")
                
                var waitTime = 0L
                val maxWaitTime = 30_000L
                while (isActive) {
                    try {
                        val initialPosition = gpsService.getPosition()
                        Log.d(TAG, "✓ Primo fix GPS: lat=${initialPosition.latitude}, lon=${initialPosition.longitude}, acc=${initialPosition.accuracy}m")
                        aggiornaNotifica("Primo fix GPS ottenuto, avvio scansioni...")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Attesa fix GPS... (${waitTime/1000}s)")
                        delay(1000L)
                        waitTime += 1000L
                        
                        if (waitTime >= maxWaitTime) {
                            Log.e(TAG, "Timeout attesa primo fix GPS dopo ${maxWaitTime/1000}s")
                            aggiornaNotifica("Errore: GPS non disponibile")
                            return@launch
                        }
                    }
                }

                while (isActive) {
                    val cycleStartTime = System.currentTimeMillis()
                    
                    try {
                        val result = warDrivingService.performScan(currentSessionId)
                        totalScansCompleted++
                        totalNetworksSaved += result.networksSaved
                        
                        val cycleDuration = System.currentTimeMillis() - cycleStartTime
                        lastScanDurationMs = cycleDuration
                        lastGPSAgeMs = result.position.getAge()

                        val distKm = totalDistanceMetres / 1000.0
                        Log.d(TAG, "Scan #$totalScansCompleted: ${result.networksSaved}/${result.networksFound} reti | " +
                                "Dist: ${String.format("%.2f", distKm)}km | GPS: ${lastGPSAgeMs}ms | Totale reti: $totalNetworksSaved")

                        aggiornaNotifica(
                            String.format(Locale.getDefault(), "Dist: %.2f km | Reti: %d | Scan #%d", 
                                distKm, totalNetworksSaved, totalScansCompleted)
                        )

                        val targetDelay = WarDrivingConfig.SCAN_INTERVAL_MS - cycleDuration
                        val actualDelay = maxOf(0L, targetDelay)
                        
                        if (actualDelay == 0L) {
                            Log.w(TAG, "⚠️ Ciclo lento (${cycleDuration}ms > ${WarDrivingConfig.SCAN_INTERVAL_MS}ms)")
                        }
                        
                        delay(actualDelay)

                    } catch (e: Exception) {
                        Log.e(TAG, "Errore durante la scansione: ${e.message}", e)
                        aggiornaNotifica("Errore: ${e.message}")
                        
                        val cycleDuration = System.currentTimeMillis() - cycleStartTime
                        val actualDelay = maxOf(0L, WarDrivingConfig.SCAN_INTERVAL_MS - cycleDuration)
                        delay(actualDelay)
                    }
                }
            } finally {
                gpsService.stopContinuousUpdates()
                
                // Aggiorna la sessione finale
                currentSessionId?.let { id ->
                    val finalSession = ScanSession(
                        id = id,
                        startTime = startTime,
                        endTime = System.currentTimeMillis(),
                        distanceMetres = totalDistanceMetres
                    )
                    try {
                        dao.updateSession(finalSession)
                        Log.d(TAG, "Sessione aggiornata: ${totalDistanceMetres/1000.0} km")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore aggiornamento sessione: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servizio fermato. Totale scansioni: $totalScansCompleted, reti salvate: $totalNetworksSaved")
        serviceJob.cancel()
        isScanning = false
        _isRunning.value = false
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