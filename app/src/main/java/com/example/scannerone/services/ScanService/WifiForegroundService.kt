package com.example.scannerone.services.ScanService

import android.annotation.SuppressLint
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class WifiForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)



    override fun onCreate() {
        super.onCreate()
        creaCanaleNotifica()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifica = creaNotifica()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notifica)
        }

        iniziaScansioneInBackground()

        return START_STICKY
    }

    private var isScanning = false

    private fun iniziaScansioneInBackground() {
        if (isScanning) return
        isScanning = true
        serviceScope.launch {
            while (isActive) {
                try {

                    Log.d("SCANNER_BG", "Scansione eseguita in background!")


                    val attesaSecondi = Random.nextDouble(3.0, 7.0)
                    delay((attesaSecondi * 1000).toLong())

                } catch (e: Exception) {
                    Log.e("SCANNER_BG", "Errore durante la scansione: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun creaCanaleNotifica() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "scanner_channel",
                "Scanner Wi-Fi Background",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun creaNotifica(): Notification {
        return NotificationCompat.Builder(this, "scanner_channel")
            .setContentTitle("Scannerone")
            .setContentText("Scansione reti Wi-Fi in corso...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}