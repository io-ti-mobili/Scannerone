package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.AnalyticsRepository
import com.example.scannerone.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionMetrics(
    val distanceKm: Double = 0.0,
    val durationMin: Double = 0.0,
    val discoveryRate: Double = 0.0,
    val spatialDensity: Double = 0.0
)

class SessionDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val sessionRepository = SessionRepository(db.sessionDao())
    private val analyticsRepository = AnalyticsRepository(db.analyticsDao())

    val allSessions = sessionRepository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<ScanSession>()
        )

    private val _selectedSessionId = MutableStateFlow<Int?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    fun selectSession(sessionId: Int?) {
        _selectedSessionId.value = sessionId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionTotalScansCount = _selectedSessionId
        .flatMapLatest { id -> sessionRepository.getSessionTotalScansCountFlow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionDiscoveryCount = _selectedSessionId
        .flatMapLatest { id -> sessionRepository.getSessionDiscoveryCountFlow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionUniqueNetworksCount = _selectedSessionId
        .flatMapLatest { id -> sessionRepository.getSessionUniqueNetworksCountFlow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val totalDistance = analyticsRepository.getTotalDistance().map { it ?: 0.0 }
    private val totalTime = analyticsRepository.getTotalTime().map { it ?: 0L }

    private val globalStats = combine(totalDistance, totalTime) { dist, time ->
        Pair(dist, time)
    }

    val sessionMetrics = combine(
        _selectedSessionId,
        allSessions,
        sessionDiscoveryCount,
        sessionUniqueNetworksCount,
        globalStats
    ) { id, sessions, discoveries, uniques, globals ->
        var totalDistKm = 0.0
        var totalDurMin = 0.0

        if (id == null) {
            totalDistKm = globals.first / 1000.0
            totalDurMin = globals.second / 60000.0
        } else {
            val session = sessions.find { it.id == id }
            if (session != null) {
                totalDistKm = session.distanceMetres / 1000.0
                val end = session.endTime ?: System.currentTimeMillis()
                totalDurMin = (end - session.startTime) / 60000.0
            }
        }

        val dRate = if (totalDurMin > 0) discoveries / totalDurMin else 0.0
        val sDensity = if (totalDistKm > 0) uniques / totalDistKm else 0.0

        SessionMetrics(totalDistKm, totalDurMin, dRate, sDensity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionMetrics())

    // Grafici a Torta (Tipi Reti)
    private fun filterZeroes(map: Map<String, Float>): Map<String, Float> = map.filterValues { it > 0f }

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryStats = _selectedSessionId
        .flatMapLatest { id -> analyticsRepository.getCategoryStatsFlow(id) }
        .map { list ->
            val result = mutableMapOf<String, Float>()
            for (item in list) {
                if (item.count > 0) {
                    val label = when (item.type) {
                        "ISP" -> "ISP"
                        "FAST_FOOD" -> "Fast Food"
                        "UNIVERSITY" -> "Universita"
                        "HOTSPOT" -> "Hotspot Personali"
                        else -> "Altro"
                    }
                    result[label] = (result[label] ?: 0f) + item.count.toFloat()
                }
            }
            filterZeroes(result)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val securityStats = _selectedSessionId
        .flatMapLatest { id -> analyticsRepository.getSecurityStatsFlow(id) }
        .map { list ->
            val result = mutableMapOf<String, Float>()
            for (item in list) {
                if (item.count > 0) {
                    val label = item.type ?: "Altro"
                    result[label] = (result[label] ?: 0f) + item.count.toFloat()
                }
            }
            filterZeroes(result)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val frequencyStats = _selectedSessionId
        .flatMapLatest { id -> analyticsRepository.getFrequencyStatsFlow(id) }
        .map { list ->
            val result = mutableMapOf<String, Float>()
            for (item in list) {
                if (item.count > 0) {
                    val label = if (item.type != null && item.type > 0.0f) "${item.type} GHz" else "Altro"
                    result[label] = (result[label] ?: 0f) + item.count.toFloat()
                }
            }
            filterZeroes(result)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 4. Andamento Tendenza Intra-Sessione (grafico lineare fine)
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionScanRecords = _selectedSessionId
        .flatMapLatest { id -> sessionRepository.getScanRecordsForSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionTrendStats = sessionScanRecords.map { records ->
        if (records.isEmpty()) return@map emptyList<Pair<String, Int>>()

        val minTime = records.minOf { it.timestamp }
        val maxTime = records.maxOf { it.timestamp }
        val durationMs = maxTime - minTime

        // Arrotondiamo il tempo di inizio al minuto precedente per avere etichette piu pulite
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = minTime
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val snappedStart = calendar.timeInMillis

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Scegliamo uno step temporale "umano" (5m, 10m, 30m, 1h) in base alla durata
        val stepMs = when {
            durationMs <= 10 * 60 * 1000 -> 2 * 60 * 1000L   // 2 minuti per sessioni brevissime
            durationMs <= 30 * 60 * 1000 -> 5 * 60 * 1000L   // 5 minuti
            durationMs <= 90 * 60 * 1000 -> 15 * 60 * 1000L  // 15 minuti
            durationMs <= 6 * 60 * 60 * 1000 -> 60 * 60 * 1000L // 1 ora
            else -> 3 * 60 * 60 * 1000L // 3 ore
        }

        val trend = mutableListOf<Pair<String, Int>>()
        var currentTime = snappedStart

        // Generiamo i punti finche non superiamo il tempo massimo
        while (currentTime <= maxTime || trend.size < 2) {
            val nextTime = currentTime + stepMs
            val count = records.count { it.timestamp in currentTime until nextTime && it.isFirstDiscovery }
            trend.add(Pair(sdf.format(Date(currentTime)), count))
            currentTime = nextTime
            if (trend.size > 20) break // Limite di sicurezza
        }
        trend
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun formatTimestamp(time: Long): String {
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
    }
}
