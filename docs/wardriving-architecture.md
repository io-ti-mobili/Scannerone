# Architettura Wardriving - Scannerone

## Descrizione Generale

Il wardriving è una scansione continua che **combina posizione GPS + reti WiFi** e salva tutto su database. Parte da UI (`WifiScreen`), avvia un **Foreground Service Android** (`WifiForegroundService`) che crea tutti i componenti e lancia un loop di scansione. A ogni ciclo:

1. **Prende posizione GPS** (dal `FusedLocationGPSServiceImpl` via Google Play Services, ~500ms)
2. **Scansiona WiFi** (`WifiScanServiceImpl` via `WifiManager.startScan()`)
3. **Unisce i dati** e salva ogni AP rilevato con lat/lon/accuratezza nel DB (`NetworkRepository.insertScannedNetwork()`)
4. **Adatta intervallo** di scansione in base al movimento (Still=60s, Walking=10-60s variabile, InVehicle=10s)

---

## Schema del Flusso

```
┌──────────────────────────────────────────────────────────────────┐
│  WifiScreen (UI)                                                 │
│  └─ toggleWarDrivingContinuo()                                   │
│       └─ startForegroundService()                                │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  WifiForegroundService  (Android Foreground Service)             │
│                                                                  │
│  onCreate():                                                     │
│    ├─ init ActivityRecognitionSource                             │
│    └─ init FusedMotionStateSource (AR + GPS → motion state)     │
│                                                                  │
│  iniziaScansioneInBackground():                                  │
│    ├─ Crea: WifiScanServiceImpl                                  │
│    ├─ Crea: FusedLocationGPSServiceImpl                          │
│    ├─ Crea: NetworkRepository + SessionRepository                │
│    ├─ Crea: ScanDirectorAgent (BDI)                              │
│    ├─ Crea: WarDrivingServiceImplV2(... ↑ tutti ↑ ...)           │
│    ├─ Attende GPS+WiFi abilitati                                 │
│    └─ Lancia: warDrivingService.runSession { ... }               │
│         │                                                        │
│         └─ callback → aggiorna notifica e StateFlow UI           │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  WarDrivingServiceImplV2.runSession()    ← IL CORE               │
│                                                                  │
│  1. Crea sessione DB (SessionRepository)                         │
│  2. gpsService.startContinuousUpdates() →                        │
│       Position fluiscono in Channel<Position>(CONFLATED)         │
│  3. Primo fix GPS (max 30s attesa)                              │
│  4. Filtra glitch GPS (Null Island, teletrasporto >60m/s)        │
│  5. Prima scansione IMMEDIATA                                    │
│  6. LOOP:                                                        │
│     ├─ Legge ultima posizione dal Channel (non bloccante)        │
│     ├─ Legge motionState da FusedMotionStateSource.state         │
│     ├─ scanAgent.askNextScanInterval(state) → intervallo DINAMICO│
│     │   └─ Walking   → AdaptiveScanStrategy (10-60s, basato      │
│     │   │              su noveltyRatio = nuovi AP / totali AP)    │
│     │   ├─ Still     → FixedScanStrategy (60s)                   │
│     │   └─ InVehicle → FixedScanStrategy (10s)                   │
│     ├─ Rispetta cooldown minimo (MIN_SCAN_COOLDOWN_MS = 2s)      │
│     ├─ performScan():                                            │
│     │    ├─ scanService.scan() → WifiScanServiceImpl             │
│     │    │    └─ wifiManager.startScan() + BroadcastReceiver     │
│     │    ├─ Per ogni AP trovato:                                 │
│     │    │    scanRepository.insertScannedNetwork(               │
│     │    │      bssid, ssid, freq, rssi,                         │
│     │    │      lat, lon, accuracy, sessionId, ...)              │
│     │    └─ Restituisce WarDrivingScanResult                     │
│     ├─ scanAgent.updateBeliefs(result) → apprende novelty        │
│     └─ onResult(result) → notifica aggiornata                    │
│                                                                  │
│  7. finally: stopContinuousUpdates, chiudi sessione              │
└──────────────────────────┬───────────────────────────────────────┘
                           │
               ┌───────────┴────────────┐
               ▼                        ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│  FusedLocationGPSService  │  │  FusedMotionStateSource   │
│                          │  │                           │
│  FLP (Google API)        │  │  Fonte PRIMARIA:          │
│  Dinamico per motion:    │  │  ActivityRecognition API  │
│    Walking   → 500ms     │  │  Fallback GPS:            │
│    Still     → 3000ms    │  │  MotionStateResolver      │
│    InVehicle → 500ms     │  │  (speed thresholds)       │
│                          │  │                           │
│  Filtra Null Island      │  │  Hysteresis: N conferme   │
│  Quality-aware caching   │  │  consecutive + debounce   │
└──────────────────────────┘  └──────────────────────────┘
```

---

## Componenti Chiave

| Componente | Ruolo |
|---|---|
| `WarDrivingServiceImplV2` | Loop principale, coordina GPS + WiFi + motion + agente BDI |
| `WifiForegroundService` | Entry point Android, gestisce lifecycle del servizio in foreground |
| `FusedLocationGPSServiceImpl` | GPS continuo via Google FusedLocationProvider, adatta frequenza al movimento |
| `FusedMotionStateSource` | Fonde Activity Recognition + GPS per determinare Still/Walking/InVehicle |
| `ScanDirectorAgent` | Agente BDI che decide intervallo scansione basato su novelty (reti nuove trovate) |
| `NetworkRepository` | Salva ogni AP con posizione GPS nel DB |
| `WarDrivingConfig` | Config centralizzata (intervalli, soglie, timeout) |

---

## V1 vs V2

- **V1** (`WarDrivingServiceImpl`): buffer circolare `@Synchronized`, scansione a intervallo fisso (5s), nessuna coscienza del movimento.
- **V2** (`WarDrivingServiceImplV2`): motion awareness, filtro glitch GPS, scansione adattiva con agente BDI, canale `Channel<Position>` thread-safe.

---

## Lista File

| # | Path | Ruolo |
|---|---|---|
| 1 | `services/WarDrivingService/WarDrivingService.kt` | Interfaccia + data class risultato |
| 2 | `services/WarDrivingService/WarDrivingConfig.kt` | Configurazione centralizzata |
| 3 | `services/WarDrivingService/WarDrivingServiceImpl.kt` | Implementazione V1 |
| 4 | `services/WarDrivingService/WarDrivingServiceImplV2.kt` | Implementazione V2 (WiGLE-style) |
| 5 | `services/ScanService/WifiForegroundService.kt` | Foreground Service entry point |
| 6 | `services/ScanService/ScanService.kt` | Interfaccia scan WiFi |
| 7 | `services/ScanService/WifiScanServiceImpl.kt` | Implementazione scan WiFi |
| 8 | `services/GPSService/GPSService.kt` | Interfaccia GPS + data class Position |
| 9 | `services/GPSService/FusedLocationGPSServiceImpl.kt` | GPS con FLP, rate adattivo |
| 10 | `services/motion/MotionState.kt` | Sealed class Still/Walking/InVehicle |
| 11 | `services/motion/MotionConfig.kt` | Profili motion e soglie |
| 12 | `services/motion/FusedMotionStateSource.kt` | Fusione AR + GPS con hysteresis |
| 13 | `services/motion/ActivityRecognitionSource.kt` | Google AR API source |
| 14 | `services/motion/MotionStateResolver.kt` | Risolutore motion via GPS (fallback) |
| 15 | `services/motion/MotionProfile.kt` | Data class profilo (GPS rate, scan interval, timeout) |
| 16 | `services/agent/ScanDirectorAgent.kt` | Agente BDI per scan adattivo |
| 17 | `services/agent/ScanEnvironmentContext.kt` | Beliefs dell'agente (novelty ratio) |
| 18 | `services/agent/ScanIntervalStrategy.kt` | Strategie Fixed + Adaptive |
| 19 | `repository/NetworkRepository.kt` | Persistenza reti scansionate |
| 20 | `repository/SessionRepository.kt` | Lifecycle sessioni di scan |
| 21 | `ui/screens/WifiScreen.kt` | UI con pulsante start/stop |
| 22 | `ui/screens/HomeScreen.kt` | Dashboard con tempo wardriving |
