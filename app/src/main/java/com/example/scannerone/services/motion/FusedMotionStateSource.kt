package com.example.scannerone.services.motion

import android.util.Log
import com.example.scannerone.services.GPSService.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sorgente di stato di movimento **fusa**: combina l'Activity Recognition API
 * (fonte primaria) con il rilevamento GPS via [MotionStateResolver] (fallback).
 *
 * ## Isteresi
 * Lo stato non cambia immediatamente ad ogni lettura: serve che il candidato
 * sia confermato N volte consecutive (configurabile via [MotionConfig]):
 * - → Still:     N = [MotionConfig.HYSTERESIS_STILL_COUNT] (default 3)
 * - → Walking:   N = [MotionConfig.HYSTERESIS_MOVE_COUNT]  (default 1)
 * - → InVehicle: N = [MotionConfig.HYSTERESIS_MOVE_COUNT]  (default 1)
 *
 * ## Debounce
 * Il cambio di stato non avviene più di una volta ogni [MotionConfig.HYSTERESIS_DEBOUNCE_MS].
 *
 * ## Sorgente
 * Se [ActivityRecognitionSource.isAvailable] è true, AR ha precedenza.
 * Quando riceve `null` da AR (permesso negato / GMS assente), usa automaticamente
 * il fallback GPS tramite [updateFromGps].
 *
 * Ciclo di vita: usa [observeArSource] all'avvio del ForegroundService,
 * poi chiama [updateFromGps] ad ogni posizione GPS.
 */
class FusedMotionStateSource(
    private val arSource: ActivityRecognitionSource
) {

    companion object {
        private const val TAG = "FusedMotion"
    }

    private val _state = MutableStateFlow<MotionState>(MotionState.Still)

    /**
     * Stato di movimento corrente, aggiornato in modo thread-safe.
     * Partenza: [MotionState.Still] (conservativo — non scansiona inutilmente).
     */
    val state: StateFlow<MotionState> = _state

    // ── Isteresi interna ─────────────────────────────────────────────────

    @Volatile private var candidate: MotionState = MotionState.Still
    @Volatile private var candidateCount: Int = 0
    @Volatile private var lastChangeMs: Long = 0L

    // Scope per osservare AR StateFlow
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Setup ────────────────────────────────────────────────────────────

    /**
     * Avvia l'osservazione del [ActivityRecognitionSource.state].
     * Chiamare dopo [ActivityRecognitionSource.start].
     */
    fun observeArSource() {
        scope.launch {
            arSource.state.collect { arMotionState ->
                if (arMotionState != null) {
                    // AR disponibile → usa quello come candidato
                    propose(arMotionState, source = "AR")
                }
                // se null → AR non pronta/disponibile; il GPS chiamerà updateFromGps
            }
        }
    }

    /**
     * Aggiorna lo stato dal GPS (fallback o conferma).
     * Viene chiamato dal [LocationListener] ad ogni fix GPS.
     * Se AR è disponibile e ha già emesso uno stato, il contributo GPS viene
     * usato solo per confermare (non sovrascrive se AR è attiva).
     */
    fun updateFromGps(current: Position, prev: Position?) {
        // Se AR è disponibile e ha già inizializzato lo stato, non sovrascrivere
        if (arSource.isAvailable && arSource.state.value != null) return

        val gpsState = MotionStateResolver.resolve(current, prev)
        propose(gpsState, source = "GPS")
    }

    /** Cancella il coroutine scope interno. Chiamare in [ActivityRecognitionSource.stop]. */
    fun cancel() {
        scope.cancel()
    }

    // ── Logica isteresi ──────────────────────────────────────────────────

    private fun propose(newState: MotionState, source: String) {
        val now = System.currentTimeMillis()

        synchronized(this) {
            if (newState == candidate) {
                candidateCount++
            } else {
                // Nuovo candidato: reset counter
                candidate = newState
                candidateCount = 1
            }

            val requiredCount = if (source == "AR") {
                1 // AR è edge-triggered e già filtrata da ML
            } else if (newState is MotionState.Still) {
                MotionConfig.HYSTERESIS_STILL_COUNT
            } else {
                MotionConfig.HYSTERESIS_MOVE_COUNT
            }

            val debounceOk = if (source == "AR") true else (now - lastChangeMs) >= MotionConfig.HYSTERESIS_DEBOUNCE_MS
            val countOk    = candidateCount >= requiredCount
            val stateChanged = newState != _state.value

            if (stateChanged && countOk && debounceOk) {
                Log.d(TAG, "[$source] Stato: ${_state.value} → $newState (confirme: $candidateCount/$requiredCount)")
                _state.value = newState
                lastChangeMs = now
                candidateCount = 0
            } else if (stateChanged) {
                Log.v(TAG, "[$source] Candidato: $newState (${candidateCount}/$requiredCount, debounce=${debounceOk})")
            }
        }
    }
}
