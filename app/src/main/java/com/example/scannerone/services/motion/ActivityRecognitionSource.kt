package com.example.scannerone.services.motion

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sorgente di stato di movimento basata sulla Google Activity Recognition Transition API.
 *
 * Usa eventi di transizione (non polling), il che minimizza il consumo batteria.
 * Lo stato è esposto come [StateFlow<MotionState?>]:
 * - `null`  → AR non disponibile o permesso negato → usare fallback GPS
 * - non-null → stato confermato dal motion coprocessor del dispositivo
 *
 * Ciclo di vita: chiama [start] quando il servizio di foreground inizia,
 * [stop] quando termina. Thread-safe.
 */
class ActivityRecognitionSource(private val context: Context) {

    companion object {
        private const val TAG = "ARSource"
        private const val ACTION = "com.example.scannerone.AR_TRANSITION"
        private const val REQUEST_CODE = 0x4172   // 'Ar' in ASCII
    }

    private val _state = MutableStateFlow<MotionState?>(null)

    /**
     * Ultimo stato rilevato dall'AR API.
     * `null` se AR non è disponibile o non ancora inizializzata.
     */
    val state: StateFlow<MotionState?> = _state

    /** True se Google Play Services è presente e il permesso è concesso. */
    val isAvailable: Boolean
        get() = hasGms() && hasPermission()

    private var pendingIntent: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null
    private var isRegistered = false

    // ── Transizioni che vogliamo monitorare ──────────────────────────────

    private val transitions = listOf(
        // STILL
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        // WALKING
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        // RUNNING → mappato su Walking
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.RUNNING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        // ON_BICYCLE → mappato su Walking
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.ON_BICYCLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        // IN_VEHICLE
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
    )

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Registra le transizioni con l'AR API.
     * No-op se GMS non è disponibile o il permesso è negato.
     */
    fun start() {
        if (!isAvailable) {
            Log.w(TAG, "AR non disponibile (GMS=${hasGms()}, perm=${hasPermission()}) — fallback GPS attivo")
            return
        }
        if (isRegistered) return

        val intent = Intent(ACTION).apply {
            setPackage(context.packageName)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        pendingIntent = pi

        val recv = buildReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(recv, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(recv, IntentFilter(ACTION))
        }
        receiver = recv

        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi)
            .addOnSuccessListener { Log.d(TAG, "Transition updates registrati") }
            .addOnFailureListener { e -> Log.e(TAG, "Errore registrazione AR: ${e.message}") }

        isRegistered = true
    }

    /** Deregistra le transizioni e rilascia le risorse. */
    fun stop() {
        if (!isRegistered) return
        pendingIntent?.let { pi ->
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pi)
                .addOnCompleteListener { pi.cancel() }
        }
        receiver?.let { context.unregisterReceiver(it) }
        pendingIntent = null
        receiver = null
        isRegistered = false
        _state.value = null
        Log.d(TAG, "Transition updates rimossi")
    }

    // ── Interno ──────────────────────────────────────────────────────────

    private fun buildReceiver() = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null || !ActivityTransitionResult.hasResult(intent)) return
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            for (event in result.transitionEvents) {
                val mapped = mapActivity(event)
                if (mapped != null) {
                    Log.d(TAG, "AR transition: ${activityName(event.activityType)} → $mapped")
                    _state.value = mapped
                }
            }
        }
    }

    private fun mapActivity(event: ActivityTransitionEvent): MotionState? = when (event.activityType) {
        DetectedActivity.STILL      -> MotionState.Still
        DetectedActivity.WALKING,
        DetectedActivity.RUNNING,
        DetectedActivity.ON_BICYCLE -> MotionState.Walking
        DetectedActivity.IN_VEHICLE -> MotionState.InVehicle
        else                        -> null   // TILTING, UNKNOWN — ignoriamo
    }

    private fun activityName(type: Int) = when (type) {
        DetectedActivity.STILL      -> "STILL"
        DetectedActivity.WALKING    -> "WALKING"
        DetectedActivity.RUNNING    -> "RUNNING"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.TILTING    -> "TILTING"
        else                        -> "UNKNOWN($type)"
    }

    private fun hasGms(): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private fun hasPermission(): Boolean {
        // ACTIVITY_RECOGNITION è richiesto solo su API 29+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
