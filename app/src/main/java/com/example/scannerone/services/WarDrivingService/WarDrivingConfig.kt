package com.example.scannerone.services.WarDrivingService

/**
 * Configurazione centralizzata per il sistema di WarDriving.
 * 
 * Tutti i parametri di timing e qualità GPS/WiFi sono definiti qui
 * per facilitare la manutenzione e future ottimizzazioni basate su euristiche.
 * 
 * Le costanti sono organizzate in sezioni logiche:
 * - GPS Configuration: parametri per l'acquisizione della posizione
 * - WiFi Scan Configuration: parametri per le scansioni WiFi
 * - GPS Quality Thresholds: soglie di qualità per filtrare fix GPS
 */
object WarDrivingConfig {
    
    // ═══════════════════════════════════════════════════════════════
    // GPS CONFIGURATION
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Intervallo minimo tra aggiornamenti GPS consecutivi (in millisecondi).
     * 
     * Valori tipici:
     * - 500ms  → Alta frequenza, fix ogni mezzo secondo. Consigliato per wardriving.
     * - 1000ms → Media frequenza, fix ogni secondo. Buon compromesso.
     * - 2000ms → Bassa frequenza, minor consumo batteria.
     * 
     * ⚠️ Nota: impostare un valore troppo basso (< 200ms) può causare:
     * - Maggior consumo batteria
     * - Overhead del sistema
     * - Fix GPS meno accurati (il chip GPS non ha tempo di stabilizzarsi)
     */
    const val GPS_UPDATE_INTERVAL_MS = 500L
    
    /**
     * Distanza minima in metri per triggerare un nuovo aggiornamento GPS.
     * 
     * Valori:
     * - 0f    → Aggiorna sempre secondo MIN_TIME, indipendentemente dallo spostamento
     * - 5f    → Aggiorna solo se ti sei spostato di almeno 5 metri
     * - 10f   → Aggiorna solo se ti sei spostato di almeno 10 metri
     * 
     * Per wardriving è consigliato 0f per catturare tutte le reti,
     * anche se sei fermo (es. a un semaforo).
     */
    const val GPS_MIN_DISTANCE_M = 0f
    
    /**
     * Età massima accettabile per una posizione GPS (in millisecondi).
     * 
     * Se la posizione nel buffer è più vecchia di questo valore,
     * viene emesso un WARNING ma la scansione procede comunque.
     * 
     * Valori tipici:
     * - 1000ms → Molto stringente, solo fix recentissimi
     * - 2000ms → Consigliato, bilanciato (max 2 secondi di ritardo)
     * - 5000ms → Permissivo, accetta fix fino a 5 secondi fa
     * 
     * ⚠️ Importante: con GPS_UPDATE_INTERVAL_MS = 500ms, la posizione
     * nel buffer non dovrebbe MAI essere più vecchia di ~1000ms in condizioni normali.
     * Se supera MAX_GPS_AGE_MS, indica un problema (GPS perso, device lento, ecc.)
     */
    const val MAX_GPS_AGE_MS = 2000L
    
    /**
     * Dimensione del buffer circolare per le posizioni GPS.
     * 
     * Il buffer mantiene le ultime N posizioni con timestamp.
     * Ad ogni scansione WiFi, viene scelta la posizione migliore tra quelle disponibili
     * (più recente con accuracy accettabile).
     * 
     * Valori:
     * - 1 → Nessun buffer, usa sempre l'ultima posizione (come prima)
     * - 3 → Consigliato, permette di scegliere tra 3 fix recenti (~1.5 secondi)
     * - 5 → Buffer più grande, utile se GPS_UPDATE_INTERVAL_MS è alto
     * 
     * ⚠️ Con GPS_UPDATE_INTERVAL_MS = 500ms e buffer = 3, hai circa:
     * 3 posizioni * 500ms = 1500ms di "storia" GPS disponibile
     */
    const val GPS_BUFFER_SIZE = 3
    
    // ═══════════════════════════════════════════════════════════════
    // WIFI SCAN CONFIGURATION
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Intervallo tra scansioni WiFi consecutive (in millisecondi).
     * 
     * Questo definisce ogni quanto esegui il ciclo completo:
     * 1. Ottieni posizione GPS dal buffer
     * 2. Esegui scansione WiFi
     * 3. Salva risultati nel database
     * 
     * Valori tipici:
     * - 3000ms  → Scansione molto frequente (~20/minuto). Alto consumo batteria.
     * - 5000ms  → Consigliato per wardriving (~12/minuto). Buon compromesso.
     * - 10000ms → Scansione meno frequente (~6/minuto). Minor consumo.
     * 
     * ⚠️ Considerazioni:
     * - Android limita le scansioni WiFi a ~4 al minuto in background (Android 9+)
     * - Ogni scansione WiFi impiega ~1-3 secondi in media
     * - Il loop è precisamente temporizzato: delay = SCAN_INTERVAL_MS - tempo_scan_effettivo
     */
    const val SCAN_INTERVAL_MS = 5000L
    
    /**
     * Timeout massimo per una scansione WiFi (in millisecondi).
     * 
     * Se il broadcast SCAN_RESULTS_AVAILABLE_ACTION non arriva entro questo tempo,
     * la scansione fallisce e viene ritornata la cache WiFi del sistema.
     * 
     * Valori tipici:
     * - 3000ms → Timeout aggressivo, fallisce velocemente
     * - 5000ms → Consigliato, stesso valore di SCAN_INTERVAL_MS
     * - 10000ms → Timeout molto permissivo (valore precedente)
     * 
     * ⚠️ Nota: un timeout alto (10s) può bloccare il loop delle scansioni.
     * È meglio un timeout uguale o leggermente inferiore a SCAN_INTERVAL_MS.
     */
    const val WIFI_SCAN_TIMEOUT_MS = 5000L
    
    // ═══════════════════════════════════════════════════════════════
    // GPS QUALITY THRESHOLDS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Accuracy massima accettabile per un fix GPS (in metri).
     * 
     * I fix con accuracy > MIN_ACCEPTABLE_ACCURACY_M vengono scartati dal buffer.
     * Serve a evitare di salvare reti WiFi con posizioni GPS molto imprecise.
     * 
     * Valori tipici:
     * - 20m  → Molto stringente, solo fix eccellenti (cielo aperto)
     * - 50m  → Consigliato, filtra fix pessimi ma accetta fix buoni/medi
     * - 100m → Permissivo, accetta anche fix in zone urbane/interno
     * 
     * ⚠️ In città o con ostacoli (edifici, alberi), l'accuracy tipica è 15-30m.
     * All'aperto con cielo libero, può scendere a 3-10m.
     */
    const val MIN_ACCEPTABLE_ACCURACY_M = 50f
    
    /**
     * Accuracy "preferita" per un fix GPS (in metri).
     * 
     * Quando il buffer contiene più posizioni, vengono preferite quelle
     * con accuracy < PREFERRED_ACCURACY_M (a parità di età).
     * 
     * Questo non scarta i fix, ma influenza la selezione della "migliore" posizione.
     * 
     * Valori tipici:
     * - 10m → Preferisci solo fix eccellenti
     * - 20m → Consigliato, buon compromesso tra qualità e disponibilità
     * - 30m → Meno stringente
     */
    const val PREFERRED_ACCURACY_M = 20f
    
    // ═══════════════════════════════════════════════════════════════
    // TIMING DIAGRAM (con valori di default)
    // ═══════════════════════════════════════════════════════════════
    // 
    //  Time (ms):  0     500   1000  1500  2000  2500  3000  3500  4000  4500  5000
    //              |-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|
    //  GPS Update: ✓           ✓           ✓           ✓           ✓           ✓
    //              [pos1]      [pos2]      [pos3]      [pos4]      [pos5]      [pos6]
    //  
    //  Buffer:     [pos1]      [pos2]      [pos3]      [pos4]      [pos5]      [pos6]
    //              (size=1)    (size=2)    (size=3)    (size=3)    (size=3)    (size=3)
    //                                                  ↓ oldest dropped
    //  
    //  WiFi Scan:  START────────────────┐                             
    //              (uses pos3 from      DONE                          
    //               buffer, age~0ms)    (saves results)               
    //                                                                 START─────────
    //                                                                 (uses pos6,
    //                                                                  age~0ms)
    //  
    //  Loop:       ├─────────── 5000ms (SCAN_INTERVAL_MS) ───────────┤
    //              └ Scan takes ~500-2000ms, delay adjusted to maintain 5000ms cycle
    // 
    // ═══════════════════════════════════════════════════════════════
}
