# BDI Agent & Motion-Aware Scan Adaptation

## Indice

1. [Pattern BDI — Beliefs, Desires, Intentions](#1-pattern-bdi--beliefs-desires-intentions)
2. [I Tre Stati di Movimento](#2-i-tre-stati-di-movimento)
3. [Determinazione dello Stato (FusedMotionStateSource)](#3-determinazione-dello-stato)
4. [Adattamento del GPS Rate](#4-adattamento-del-gps-rate)
5. [Adattamento dell'Intervallo di Scansione (BDI)](#5-adattamento-dellintervallo-di-scansione)
6. [Flusso Completo nel Loop di WarDrivingServiceImplV2](#6-flusso-completo-nel-loop-di-wardrivingserviceimplv2)
7. [Mappa Dipendenze](#7-mappa-dipendenze)

---

## 1. Pattern BDI — Beliefs, Desires, Intentions

Il pattern **BDI** (Beliefs-Desires-Intentions) è un modello di agenti intelligenti applicato qui al sistema di wardriving per decidere **quando scansionare** in modo dinamico.

| Componente BDI | Classe | Ruolo |
|---|---|---|
| **Beliefs** (Credenze) | `ScanEnvironmentContext` | Ciò che l'agente "sa" del mondo: quante reti ha trovato, quante sono nuove, novelty ratio |
| **Desires** (Desideri) | Implicito nel design | Scansionare solo quando serve: risparmiare batteria ma non perdere reti nuove |
| **Intentions** (Intenzioni) | `ScanIntervalStrategy` | Il piano d'azione scelto: `FixedScanStrategy` o `AdaptiveScanStrategy` |

### Beliefs — `ScanEnvironmentContext.kt`

```kotlin
data class ScanEnvironmentContext(
    val totalNetworksInLastScan: Int = 0,
    val newNetworksInLastScan: Int = 0,
    val isGpsAccuracyGood: Boolean = true
) {
    val noveltyRatio: Double
        get() = if (totalNetworksInLastScan == 0) 0.0
        else newNetworksInLastScan.toDouble() / totalNetworksInLastScan
}
```

`noveltyRatio` va da `0.0` (nessuna rete nuova, ambiente già noto) a `1.0` (tutte reti nuove, ambiente sconosciuto).

### Intentions — `ScanIntervalStrategy.kt`

```kotlin
interface ScanIntervalStrategy {
    fun calculateNextInterval(context: ScanEnvironmentContext): Long
}

// Strategia fissa: ignora il contesto
class FixedScanStrategy(private val intervalMs: Long) : ScanIntervalStrategy {
    override fun calculateNextInterval(context: ScanEnvironmentContext): Long = intervalMs
}

// Strategia adattiva: scala l'intervallo in base alla novelty
class AdaptiveScanStrategy(
    private val minMs: Long,
    private val maxMs: Long
) : ScanIntervalStrategy {
    override fun calculateNextInterval(context: ScanEnvironmentContext): Long {
        val effectiveNovelty = (context.noveltyRatio.coerceIn(0.0, 0.25) * 4.0)
        val range = maxMs - minMs
        val reduction = (range * effectiveNovelty).toLong()
        return (maxMs - reduction).coerceIn(minMs, maxMs)
    }
}
```

La formula dell'`AdaptiveScanStrategy`:

```
effectiveNovelty = min(noveltyRatio, 0.25) * 4.0
                = va da 0.0 a 1.0 (saturo a noveltyRatio=0.25)

reduction = (maxMs - minMs) * effectiveNovelty
interval  = maxMs - reduction
          = da maxMs (novità 0) a minMs (novità ≥25%)
```

Con `minMs=10s`, `maxMs=60s`:
- Novelty 0% → intervallo 60s (niente di nuovo, aspetta)
- Novelty 25%+ → intervallo 10s (tante reti nuove, scansiona subito)
- Novelty 10% → intervallo 50s

### Agente — `ScanDirectorAgent.kt`

```kotlin
class ScanDirectorAgent {
    private var currentContext = ScanEnvironmentContext()

    private val walkingStrategy = AdaptiveScanStrategy(minMs = 10_000L, maxMs = 60_000L)
    private val stillStrategy = FixedScanStrategy(intervalMs = 60_000L)
    private val vehicleStrategy = FixedScanStrategy(intervalMs = 10_000L)

    // L'agente percepisce il risultato dell'ultimo scan e aggiorna le credenze
    fun updateBeliefs(result: WarDrivingScanResult) {
        currentContext = ScanEnvironmentContext(
            totalNetworksInLastScan = result.networksFound,
            newNetworksInLastScan = result.networksSaved
        )
    }

    // L'agente delibera: quanto aspettare per il prossimo scan?
    fun askNextScanInterval(currentState: MotionState): Long {
        val strategyToUse = when (currentState) {
            MotionState.Walking -> walkingStrategy
            MotionState.Still -> stillStrategy
            MotionState.InVehicle -> vehicleStrategy
        }
        return strategyToUse.calculateNextInterval(currentContext)
    }
}
```

---

## 2. I Tre Stati di Movimento

```kotlin
sealed class MotionState {
    object Still     : MotionState()
    object Walking   : MotionState()
    object InVehicle : MotionState()
}
```

Ogni stato ha un **profilo operativo** definito in `MotionConfig.kt`:

| Stato | GPS Rate | Scan Interval (base) | Still Timeout | Strategia BDI |
|---|---|---|---|---|
| **Still** | 3000ms | 60s | 0 | `FixedScanStrategy(60s)` |
| **Walking** | 500ms | 30s | 15s | `AdaptiveScanStrategy(10-60s)` |
| **InVehicle** | 500ms | 10s | 15s | `FixedScanStrategy(10s)` |

```kotlin
object MotionConfig {
    // Soglie di velocità
    const val SPEED_STILL_MAX = 0.5f      // ≈ 1.8 km/h
    const val SPEED_VEHICLE_MIN = 2.8f    // ≈ 10 km/h
    const val FALLBACK_DIST_M = 8.0f      // soglia per stima speed senza chip speed

    // Isteresi
    const val HYSTERESIS_STILL_COUNT = 3
    const val HYSTERESIS_MOVE_COUNT = 1
    const val HYSTERESIS_DEBOUNCE_MS = 3_000L

    val PROFILE_STILL = MotionProfile(gpsRateMs = 3_000L, scanIntervalMs = 60_000L, stillTimeoutMs = 0L)
    val PROFILE_WALKING = MotionProfile(gpsRateMs = 500L, scanIntervalMs = 30_000L, stillTimeoutMs = 15_000L)
    val PROFILE_IN_VEHICLE = MotionProfile(gpsRateMs = 500L, scanIntervalMs = 10_000L, stillTimeoutMs = 15_000L)

    fun profileFor(state: MotionState): MotionProfile = when (state) {
        is MotionState.Still     -> PROFILE_STILL
        is MotionState.Walking   -> PROFILE_WALKING
        is MotionState.InVehicle -> PROFILE_IN_VEHICLE
    }
}
```

Il `MotionProfile` è un semplice contenitore:

```kotlin
data class MotionProfile(
    val gpsRateMs: Long,
    val scanIntervalMs: Long,
    val stillTimeoutMs: Long
)
```

---

## 3. Determinazione dello Stato

Due fonti, fuse da `FusedMotionStateSource`:

### Fonte Primaria: `ActivityRecognitionSource` (Google AR Transition API)

Usa eventi di transizione (non polling) — minimo consumo batteria. Ascolta queste transizioni:

| Attività AR | Mappata a |
|---|---|
| `STILL` | `MotionState.Still` |
| `WALKING` | `MotionState.Walking` |
| `RUNNING` | `MotionState.Walking` |
| `ON_BICYCLE` | `MotionState.Walking` |
| `IN_VEHICLE` | `MotionState.InVehicle` |
| `TILTING`, `UNKNOWN` | `null` (ignorato) |

```kotlin
private fun mapActivity(event: ActivityTransitionEvent): MotionState? = when (event.activityType) {
    DetectedActivity.STILL      -> MotionState.Still
    DetectedActivity.WALKING,
    DetectedActivity.RUNNING,
    DetectedActivity.ON_BICYCLE -> MotionState.Walking
    DetectedActivity.IN_VEHICLE -> MotionState.InVehicle
    else                        -> null
}
```

### Fallback GPS: `MotionStateResolver`

Usato solo se AR non è disponibile (GMS assente o permesso `ACTIVITY_RECOGNITION` negato).

```kotlin
object MotionStateResolver {
    fun resolve(position: Position, prevPosition: Position?): MotionState {
        val speed = if (position.hasSpeed) position.speed
        else estimateSpeed(position, prevPosition)
        return fromSpeed(speed)
    }

    fun fromSpeed(speedMs: Float): MotionState = when {
        speedMs < MotionConfig.SPEED_STILL_MAX   -> MotionState.Still
        speedMs < MotionConfig.SPEED_VEHICLE_MIN -> MotionState.Walking
        else                                      -> MotionState.InVehicle
    }

    private fun estimateSpeed(current: Position, prev: Position?): Float {
        prev ?: return 0f
        val distM = prev.distanceTo(current)
        val dtMs = current.timestamp - prev.timestamp
        if (dtMs <= 0L) return 0f
        val speedMs = distM / (dtMs / 1_000f)
        return if (distM < MotionConfig.FALLBACK_DIST_M) 0f else speedMs
    }
}
```

Soglie di velocità:
- `< 0.5 m/s` (≈1.8 km/h) → **Still**
- `0.5 – 2.8 m/s` (1.8-10 km/h) → **Walking**
- `> 2.8 m/s` (≈10 km/h) → **InVehicle**

La stima senza chip speed usa `FALLBACK_DIST_M = 8m` per evitare falsi Walking causati dal rumore GPS (±5-10m).

### Hysteresis: `FusedMotionStateSource`

Lo stato non cambia immediatamente: serve conferma multipla per evitare flapping.

```kotlin
private fun propose(newState: MotionState, source: String) {
    val now = System.currentTimeMillis()

    synchronized(this) {
        if (newState == candidate) {
            candidateCount++
        } else {
            candidate = newState
            candidateCount = 1
        }

        val requiredCount = if (source == "AR") {
            1  // AR è già filtrata dal ML di Google
        } else if (newState is MotionState.Still) {
            MotionConfig.HYSTERESIS_STILL_COUNT  // 3
        } else {
            MotionConfig.HYSTERESIS_MOVE_COUNT   // 1
        }

        val debounceOk = if (source == "AR") true
            else (now - lastChangeMs) >= MotionConfig.HYSTERESIS_DEBOUNCE_MS
        val countOk = candidateCount >= requiredCount
        val stateChanged = newState != _state.value

        if (stateChanged && countOk && debounceOk) {
            _state.value = newState
            lastChangeMs = now
            candidateCount = 0
        }
    }
}
```

| Regola | Still | Walking | InVehicle |
|---|---|---|---|
| Conferme necessarie (fallback GPS) | **3** | **1** | **1** |
| Conferme necessarie (AR API) | **1** | **1** | **1** |
| Debounce minimo | 3s | 3s | 3s |

**Logica**: AR è già filtrata dal machine learning di Google, quindi non serve isteresi aggiuntiva. Con GPS serve isteresi perché il rumore del segnale può far fluttuare la velocità.

#### Schema fusione

```
ActivityRecognitionSource.state (AR)
    │  null? → aspetta GPS
    │  non-null → propose() con source="AR" (conferma=1, no debounce)
    ▼
┌─────────────────────┐
│ FusedMotionStateSource│
│  propose()           │
│  - isteresi          │
│  - debounce          │
│  - source="AR"/"GPS" │
└────────┬────────────┘
         │ state: StateFlow<MotionState>
         ▼
   Consumato da:
   - WarDrivingServiceImplV2 (scan interval)
   - FusedLocationGPSServiceImpl (GPS rate)

FusedLocationGPSServiceImpl.onLocationResult()
    │  chiama updateFromGps(current, prev)
    │  (no-op se AR è disponibile)
    ▼
MotionStateResolver.resolve()
    │  speed < 0.5 → Still
    │  0.5-2.8 → Walking
    │  > 2.8 → InVehicle
    ▼
FusedMotionStateSource.propose() con source="GPS"
```

---

## 4. Adattamento del GPS Rate

`FusedLocationGPSServiceImpl` osserva lo stato di movimento e adatta la frequenza GPS.

```kotlin
override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
    // ...

    // Avvia con profilo Walking (alta frequenza)
    applyRate(MotionConfig.PROFILE_WALKING.gpsRateMs)  // 500ms

    // Adatta il rate ai cambiamenti di stato
    scope.launch {
        fusedMotionSource.state
            .drop(1)  // salta lo stato iniziale Still
            .collect { state ->
                if (!isContinuousActive) return@collect
                val profile = MotionConfig.profileFor(state)
                applyRate(profile.gpsRateMs)
            }
    }
}

private fun applyRate(newIntervalMs: Long) {
    if (newIntervalMs == currentIntervalMs) return

    val priority = if (newIntervalMs <= MotionConfig.PROFILE_WALKING.gpsRateMs)
        Priority.PRIORITY_HIGH_ACCURACY
    else
        Priority.PRIORITY_BALANCED_POWER_ACCURACY

    val request = LocationRequest.Builder(priority, newIntervalMs)
        .setMinUpdateIntervalMillis(newIntervalMs / 2)
        .setMinUpdateDistanceMeters(WarDrivingConfig.GPS_MIN_DISTANCE_M)
        .build()

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    currentIntervalMs = newIntervalMs
}
```

| Stato | GPS Rate | Priorità FLP | Motivo |
|---|---|---|---|
| **Walking** | 500ms | `HIGH_ACCURACY` | Serve posizione precisa e frequente per wardriving pedonale |
| **InVehicle** | 500ms | `HIGH_ACCURACY` | Stessa frequenza: in auto ci si sposta veloce, servono fix frequenti |
| **Still** | 3000ms | `BALANCED_POWER` | Si è fermi, non servono aggiornamenti GPS continui → risparmio batteria |

Il `drop(1)` iniziale è importante: parte sempre con 500ms (Walking) per avere subito GPS frequente, poi appena arriva il primo stato da AR/GPS si adatta.

---

## 5. Adattamento dell'Intervallo di Scansione

Il `WarDrivingServiceImplV2` integra tutto nel loop principale:

```kotlin
// Leggi lo stato dalla sorgente unificata
val state = fusedMotionSource.state.value

// L'agente BDI calcola l'intervallo dinamico
val dynamicScanIntervalMs = scanAgent.askNextScanInterval(state)

val cooldownOk: Boolean = timeSinceScan >= MIN_SCAN_COOLDOWN_MS      // 2s
val isTimeTrigger: Boolean = timeSinceScan >= dynamicScanIntervalMs

if (!cooldownOk || !isTimeTrigger) continue
```

### Riepilogo per stato

| Stato | Strategia | Intervallo | Comportamento |
|---|---|---|---|
| **Still** | `FixedScanStrategy(60s)` | **60s** fisso | Non ci si muove, l'ambiente non cambia → scan radi |
| **Walking** | `AdaptiveScanStrategy(10-60s)` | **10-60s** variabile | Si esplorano nuove zone → scan frequente se trovi roba nuova, rallenta se è sempre la stessa |
| **InVehicle** | `FixedScanStrategy(10s)` | **10s** fisso | Ci si sposta veloce → scan molto frequenti per non perdere reti |

### Walking: AdaptiveScanStrategy in dettaglio

```
noveltyRatio = networksSaved / networksFound

Esempi concreti:
- 0/10 reti nuove (novelty=0.0)   → effectiveNovelty=0.0   → interval=60s
- 1/10 reti nuove (novelty=0.1)   → effectiveNovelty=0.4   → interval=60-(50*0.4)=40s
- 2/10 reti nuove (novelty=0.2)   → effectiveNovelty=0.8   → interval=60-(50*0.8)=20s
- 3/10 reti nuove (novelty=0.3)   → effectiveNovelty=1.0   → interval=10s (saturo)
- 10/10 reti nuove (novelty=1.0)  → effectiveNovelty=1.0   → interval=10s (saturo)
```

Il saturatore a 0.25 evita di richiedere novelty al 100% per ottenere l'intervallo minimo. Basta che il 25% delle reti sia nuovo per scansionare alla massima frequenza.

### Update delle credenze

Dopo ogni scan riuscito:

```kotlin
scanAgent.updateBeliefs(result)
// → aggiorna currentContext con i nuovi networksFound/networksSaved
// → il prossimo askNextScanInterval() userà i nuovi valori
```

---

## 6. Flusso Completo nel Loop di WarDrivingServiceImplV2

```
WarDrivingServiceImplV2.runSession()
│
├─ 1. Crea sessione DB (SessionRepository)
├─ 2. gpsService.startContinuousUpdates() → fluisce Position su Channel(CONFLATED)
│     └─ Ogni fix GPS: filtra Null Island + teleport >60m/s
│        poi positionChannel.trySend(pos)
│
├─ 3. Attende primo fix GPS (max 30s)
│
├─ 4. Prima scansione IMMEDIATA (MotionState.Still, scansPerMin=0)
│     └─ scanAgent.updateBeliefs(result)
│     └─ onResult(result)
│
├─ 5. LOOP PRINCIPALE:
│   │
│   while (isActive) {
│   │
│   ├─ withTimeoutOrNull(500ms) { positionChannel.receive() }
│   │   ↑ attesa non bloccante, se non arriva posizione prosegue
│   │
│   ├─ Legge: distSince, totalDist, latestPos, prevPos (synchronized)
│   │
│   ├─ val state = fusedMotionSource.state.value
│   │   ↑ Still / Walking / InVehicle
│   │
│   ├─ val dynamicScanIntervalMs = scanAgent.askNextScanInterval(state)
│   │   ↑ Walking → AdaptiveScanStrategy(10-60s)
│   │     Still   → FixedScanStrategy(60s)
│   │     Vehicle → FixedScanStrategy(10s)
│   │
│   ├─ cooldownOk  = timeSinceScan >= MIN_SCAN_COOLDOWN_MS (2s)
│   │   isTimeTrigger = timeSinceScan >= dynamicScanIntervalMs
│   │
│   ├─ if (!cooldownOk || !isTimeTrigger) continue
│   │   ↑ rispetta cooldown minimo + intervallo dinamico
│   │
│   ├─ if (pos.accuracy > 50m) continue
│   │   ↑ salta scan se GPS è troppo impreciso
│   │
│   ├─ performScan(sessionId, pos, totalDist, state, scansPerMin)
│   │   ├─ scanService.scan() → WifiManager.startScan() + BroadcastReceiver
│   │   ├─ Per ogni AP: scanRepository.insertScannedNetwork(bssid, ssid, ...)
│   │   └─ Restituisce WarDrivingScanResult
│   │
│   ├─ scanAgent.updateBeliefs(result)
│   │   ↑ aggiorna il novelty ratio per la prossima iterazione
│   │
│   └─ onResult(result)
│       ↑ callback → WifiForegroundService aggiorna notifica e StateFlow UI
│   }
│
└─ finally: stopContinuousUpdates, chiudi sessione DB
```

### Codice rilevante dal loop (WarDrivingServiceImplV2.kt, righe 154-209)

```kotlin
while (currentCoroutineContext().isActive) {
    withTimeoutOrNull(LOOP_POLL_MS) { positionChannel.receive() }

    val now = System.currentTimeMillis()
    val timeSinceScan: Long = now - lastScanTime

    val distSince: Double; val totalDist: Double
    val latestPos: Position?; val prevPos: Position?
    synchronized(lock) {
        distSince = distSinceLastScanM; totalDist = totalDistM
        latestPos = lastCallbackPos; prevPos = prevCallbackPos
    }

    val posToUse: Position = latestPos ?: continue

    // Legge lo stato dalla sorgente unificata
    val state = fusedMotionSource.state.value

    // L'agente BDI decide l'intervallo
    val dynamicScanIntervalMs = scanAgent.askNextScanInterval(state)

    val cooldownOk: Boolean = timeSinceScan >= MIN_SCAN_COOLDOWN_MS
    val isTimeTrigger: Boolean = timeSinceScan >= dynamicScanIntervalMs

    if (!cooldownOk || !isTimeTrigger) continue

    if (posToUse.accuracy > WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M) continue

    runCatching { performScan(sessionId, posToUse, totalDist, state, 0.0) }
        .onSuccess { partialResult ->
            scanCount++; lastScanTime = System.currentTimeMillis()
            synchronized(lock) { distSinceLastScanM = 0.0 }
            recentScanTs.add(lastScanTime)
            val cutoff: Long = lastScanTime - 60_000L
            recentScanTs.removeAll { ts: Long -> ts < cutoff }
            val scansPerMin = recentScanTs.size.toDouble()

            val result = partialResult.copy(scansPerMinute = scansPerMin)

            // L'agente aggiorna le sue credenze
            scanAgent.updateBeliefs(result)

            onResult(result)
        }
}
```

---

## 7. Mappa Dipendenze

```
                          ┌──────────────────────────────┐
                          │   WarDrivingServiceImplV2    │
                          │   (loop principale)          │
                          └──┬──────┬────────┬───────────┘
                             │      │        │
                  legge stato│      │        │ usa MotionConfig
                             │      │        │ per profileFor()
                             ▼      │        │
              ┌────────────────┐   │        ▼
              │FusedMotionState│   │  ┌────────────┐
              │Source          │   │  │MotionConfig│
              │ .state: StateFlow   │  │(soglie+profili)
              └───────┬────────┘   │  └────────────┘
                      │            │
            ┌─────────┴──┐        │
            ▼            ▼        │
    ┌────────────┐ ┌──────────┐  │
    │ActivityRec │ │MotionState│  │
    │Source (AR) │ │Resolver   │  │
    │(primario)  │ │(fallback) │  │
    └────────────┘ └──────────┘  │
                                  │
                     ┌────────────┘
                     ▼
          ┌──────────────────────┐
          │   ScanDirectorAgent   │
          │   (BDI Agent)         │
          ├──────────────────────┤
          │ Beliefs:              │
          │  ScanEnvironmentContext│
          │  (noveltyRatio)       │
          ├──────────────────────┤
          │ Intentions:           │
          │  Walking → Adaptive   │
          │  Still   → Fixed(60s) │
          │  Vehicle → Fixed(10s) │
          └──────────────────────┘
                     ▲
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
  ┌──────────────┐   ┌──────────────────┐
  │FixedScanStrat│   │AdaptiveScanStrat │
  │(interval fisso) │(intervallo variabile│
  └──────────────┘   │ basato su novelty)│
                     └──────────────────┘

    FusedLocationGPSServiceImpl
         │ osserva fusedMotionSource.state
         │ (con drop(1))
         ▼
    applyRate(profile.gpsRateMs)
         │ Still     → 3000ms, BALANCED_POWER
         │ Walking   → 500ms,  HIGH_ACCURACY
         │ InVehicle → 500ms,  HIGH_ACCURACY
         ▼
    FLP (Google FusedLocationProvider)
```

---

## Riepilogo: cosa cambia in base allo stato selezionato

| Aspetto | Still | Walking | InVehicle |
|---|---|---|---|
| **GPS rate** | 3000ms | 500ms | 500ms |
| **Priorità GPS** | `BALANCED_POWER` | `HIGH_ACCURACY` | `HIGH_ACCURACY` |
| **Scan interval** | 60s fisso | 10-60s variabile (novelty-based) | 10s fisso |
| **Strategia BDI** | `FixedScanStrategy` | `AdaptiveScanStrategy` | `FixedScanStrategy` |
| **Isteresi → Still** | 3 conferme | 3 conferme | 3 conferme |
| **Isteresi → Move** | 1 conferma | 1 conferma | 1 conferma |
| **Distanza cumulativa** | NON tracciata | Tracciata | Tracciata |
| **Batteria** | Basso consumo | Alto consumo | Alto consumo |
| **Copertura reti** | Bassa (solo reti in zona) | Alta (esplorazione) | Molto alta (movimento veloce) |
