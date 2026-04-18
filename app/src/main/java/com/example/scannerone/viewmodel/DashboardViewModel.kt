package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TimeFilter(val label: String) {
    TODAY("Oggi"),
    LAST_7_DAYS("Ultima Settimana"),
    LAST_4_WEEKS("Mensile (4 Sett)"),
    LAST_6_MONTHS("Ultimi 6 Mesi"),
    LAST_YEAR("Ultimo Anno")
}

data class SessionMetrics(
    val distanceKm: Double = 0.0,
    val durationMin: Double = 0.0,
    val discoveryRate: Double = 0.0,
    val spatialDensity: Double = 0.0
)

data class HallOfFameRecords(
    val longestSession: ScanSession? = null,
    val mostDistanceSession: ScanSession? = null,
    val mostUniquesSession: ScanSession? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )

    // 1. Statistiche Globali
    val totalNetworksCount = repository.getTotalNetworksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScansCount = repository.getTotalScansCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val totalDistance = repository.getTotalDistance()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalTime = repository.getTotalTime()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val allSessions = repository.getAllSessions()
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

    val mostUniquesSessionId = repository.getSessionIdWithMostUniqueNetworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hallOfFame = combine(allSessions, mostUniquesSessionId) { sessions, uniquesId ->
        if (sessions.isEmpty()) return@combine HallOfFameRecords()
        
        val longest = sessions.maxByOrNull { (it.endTime ?: System.currentTimeMillis()) - it.startTime }
        val distance = sessions.maxByOrNull { it.distanceMetres }
        val uniques = uniquesId?.let { uid -> sessions.find { it.id == uid } }
        
        HallOfFameRecords(longest, distance, uniques)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HallOfFameRecords())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionNetworks = _selectedSessionId
        .flatMapLatest { id -> repository.getNetworksForSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionScanRecords = _selectedSessionId
        .flatMapLatest { id -> repository.getScanRecordsForSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionTotalScansCount = sessionScanRecords.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sessionUniqueNetworksCount = sessionScanRecords.map { records ->
        records.count { it.isFirstDiscovery }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sessionMetrics = combine(_selectedSessionId, allSessions, sessionUniqueNetworksCount) { id, sessions, uniques ->
        val selectedSessions = if (id == null) sessions else sessions.filter { it.id == id }
        var totalDistKm = 0.0
        var totalDurMin = 0.0
        
        val now = System.currentTimeMillis()
        for (session in selectedSessions) {
            totalDistKm += session.distanceMetres / 1000.0
            val end = session.endTime ?: now
            val start = session.startTime
            totalDurMin += (end - start) / 60000.0
        }
        
        val dRate = if (totalDurMin > 0) uniques / totalDurMin else 0.0
        val sDensity = if (totalDistKm > 0) uniques / totalDistKm else 0.0
        
        SessionMetrics(totalDistKm, totalDurMin, dRate, sDensity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionMetrics())

    val sessionTrendStats = sessionScanRecords.map { records ->
        if (records.isEmpty()) return@map emptyList<Pair<String, Int>>()
        
        val minTime = records.minOf { it.timestamp }
        val maxTime = records.maxOf { it.timestamp }
        val duration = maxTime - minTime
        
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (duration <= 0) return@map listOf(Pair(sdf.format(Date(minTime)), records.count { it.isFirstDiscovery }))
        
        val bucketSize = duration / 7.0
        val trend = mutableListOf<Pair<String, Int>>()
        
        for (i in 0 until 7) {
            val start = minTime + (i * bucketSize).toLong()
            val end = if (i == 6) maxTime + 1 else minTime + ((i + 1) * bucketSize).toLong()
            val count = records.count { it.timestamp in start until end && it.isFirstDiscovery }
            trend.add(Pair(sdf.format(Date(start)), count))
        }
        trend
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Grafici e Analisi
    private fun filterZeroes(map: Map<String, Float>): Map<String, Float> = map.filterValues { it > 0f }

    val categoryStats = sessionNetworks.map { networkList ->
        var isp = 0f; var fastFood = 0f; var uni = 0f; var hotspot = 0f; var other = 0f
        networkList.forEach { net ->
            when(net.category) {
                "ISP" -> isp += 1f
                "FAST_FOOD" -> fastFood += 1f
                "UNIVERSITY" -> uni += 1f
                "HOTSPOT" -> hotspot += 1f
                "OTHER" -> other += 1f
                else -> other += 1f
            }
        }
        filterZeroes(mapOf(
            "ISP (Tim, Vodafone...)" to isp,
            "Fast Food" to fastFood,
            "Università" to uni,
            "Hotspot Personali" to hotspot,
            "Altro" to other
        ))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    val securityStats = sessionNetworks.map { networkList ->
        var open = 0f; var wep = 0f; var wpa = 0f; var wpa2 = 0f; var wpa3 = 0f; var other = 0f
        networkList.forEach { net ->
            val cap = net.capabilities.uppercase()
            when {
                cap.contains("WPA3") || cap.contains("OWE") || cap.contains("SAE") -> wpa3 += 1f
                cap.contains("WPA2") -> wpa2 += 1f
                cap.contains("WPA-") || cap.contains("WPA1") -> wpa += 1f
                cap.contains("WEP") -> wep += 1f
                cap.isEmpty() || cap == "[ESS]" || cap.contains("OPEN") || cap.contains("NONE") -> open += 1f
                else -> other += 1f
            }
        }
        filterZeroes(mapOf("Open" to open, "WEP" to wep, "WPA" to wpa, "WPA2" to wpa2, "WPA3" to wpa3, "Altro" to other))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val frequencyStats = sessionNetworks.map { networkList ->
        var freq24 = 0f; var freq5 = 0f; var freq6 = 0f
        networkList.forEach { net ->
            when {
                net.frequency in 2400..2500 -> freq24 += 1f
                net.frequency in 5000..5900 -> freq5 += 1f
                net.frequency > 5900 -> freq6 += 1f
            }
        }
        filterZeroes(mapOf("2.4 GHz" to freq24, "5 GHz" to freq5, "6 GHz" to freq6))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 3. Andamento nel tempo
    private val _timeFilter = MutableStateFlow(TimeFilter.TODAY)
    val timeFilter = _timeFilter.asStateFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    val trendStats = combine(
        repository.getNetworkDiscoveryTimes(),
        _timeFilter
    ) { discoveryTimes, filter ->
        computeTrend(discoveryTimes, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanTrendStats = combine(
        repository.getAllScanTimes(),
        _timeFilter
    ) { scanTimes, filter ->
        computeTrend(scanTimes, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun computeTrend(times: List<Long>, filter: TimeFilter): List<Pair<String, Int>> {
        if (times.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val snappedNow = calendar.timeInMillis

        val hourMs = 3600000L
        val dayMs = 86400000L
        val weekMs = dayMs * 7L
        val monthMs = dayMs * 30L

        var startTime = 0L
        var numSteps = 0
        var stepDuration = 0L
        var formatPattern = ""

        when (filter) {
            TimeFilter.TODAY -> {
                startTime = snappedNow - (22 * hourMs)
                numSteps = 12
                stepDuration = 2 * hourMs
                formatPattern = "HH'h'"
            }
            TimeFilter.LAST_7_DAYS -> {
                startTime = now - (7 * dayMs)
                numSteps = 7
                stepDuration = dayMs
                formatPattern = "dd/MM"
            }
            TimeFilter.LAST_4_WEEKS -> {
                startTime = now - (4 * weekMs)
                numSteps = 4
                stepDuration = weekMs
                formatPattern = "W'a'"
            }
            TimeFilter.LAST_6_MONTHS -> {
                startTime = now - (6 * monthMs)
                numSteps = 6
                stepDuration = monthMs
                formatPattern = "MMM"
            }
            TimeFilter.LAST_YEAR -> {
                startTime = now - (12 * monthMs)
                numSteps = 12
                stepDuration = monthMs
                formatPattern = "MMM"
            }
        }

        val trend = mutableListOf<Pair<String, Int>>()
        val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())

        for (i in 0 until numSteps) {
            val stepStart = startTime + (i * stepDuration)
            val stepEnd = stepStart + stepDuration

            val countInStep = times.count { it in stepStart until stepEnd }
            
            val timeString = if (filter == TimeFilter.LAST_4_WEEKS) {
                "${numSteps - i} sett fa"
            } else {
                sdf.format(Date(stepStart))
            }
            trend.add(Pair(timeString, countInStep))
        }

        return trend
    }

    fun formatTimestamp(time: Long): String {
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
    }
}
