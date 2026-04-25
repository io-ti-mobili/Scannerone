package com.example.scannerone.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.R
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.AnalyticsRepository
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

enum class TimeFilter(@StringRes val labelRes: Int) {
    TODAY(R.string.time_filter_today),
    LAST_7_DAYS(R.string.time_filter_last_7_days),
    LAST_4_WEEKS(R.string.time_filter_last_4_weeks),
    LAST_6_MONTHS(R.string.time_filter_last_6_months),
    LAST_YEAR(R.string.time_filter_last_year)
}

data class TrendParams(
    val startTime: Long,
    val endTime: Long,
    val bucketSize: Long,
    val numSteps: Int,
    val formatPattern: String
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AnalyticsRepository(
        AppDatabase.getDatabase(application).analyticsDao()
    )

    // 1. Statistiche Globali (Numeri in evidenza)
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

    // 2. Andamento nel tempo (Esteso su tutto il db)
    private val _discoveryTimeFilter = MutableStateFlow(TimeFilter.TODAY)
    val discoveryTimeFilter = _discoveryTimeFilter.asStateFlow()

    private val _scanTimeFilter = MutableStateFlow(TimeFilter.TODAY)
    val scanTimeFilter = _scanTimeFilter.asStateFlow()

    private val _sessionsTimeFilter = MutableStateFlow(TimeFilter.TODAY)
    val sessionsTimeFilter = _sessionsTimeFilter.asStateFlow()

    fun setDiscoveryTimeFilter(filter: TimeFilter) {
        _discoveryTimeFilter.value = filter
    }

    fun setScanTimeFilter(filter: TimeFilter) {
        _scanTimeFilter.value = filter
    }

    fun setSessionsTimeFilter(filter: TimeFilter) {
        _sessionsTimeFilter.value = filter
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val trendStats = _discoveryTimeFilter.flatMapLatest { filter ->
        val params = getTrendParams(filter)
        if (params == null) kotlinx.coroutines.flow.flowOf(emptyList<Pair<String, Int>>())
        else repository.getDiscoveryTrendStats(params.startTime, params.endTime, params.bucketSize)
            .map { formatBucketStats(it, params, filter) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Pair<String, Int>>())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scanTrendStats = _scanTimeFilter.flatMapLatest { filter ->
        val params = getTrendParams(filter)
        if (params == null) kotlinx.coroutines.flow.flowOf(emptyList<Pair<String, Int>>())
        else repository.getScanTrendStats(params.startTime, params.endTime, params.bucketSize)
            .map { formatBucketStats(it, params, filter) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Pair<String, Int>>())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessionTrendStats = _sessionsTimeFilter.flatMapLatest { filter ->
        val params = getTrendParams(filter)
        if (params == null) kotlinx.coroutines.flow.flowOf(emptyList<Pair<String, Int>>())
        else repository.getSessionTrendStats(params.startTime, params.endTime, params.bucketSize)
            .map { formatBucketStats(it, params, filter) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Pair<String, Int>>())

    private fun getTrendParams(filter: TimeFilter): TrendParams? {
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

        return when (filter) {
            TimeFilter.TODAY -> TrendParams(
                startTime = snappedNow - (22 * hourMs),
                numSteps = 12,
                bucketSize = 2 * hourMs,
                formatPattern = "HH:mm",
                endTime = snappedNow + (2 * hourMs)
            )
            TimeFilter.LAST_7_DAYS -> TrendParams(
                startTime = now - (7 * dayMs),
                numSteps = 7,
                bucketSize = dayMs,
                formatPattern = "dd/MM",
                endTime = now + dayMs
            )
            TimeFilter.LAST_4_WEEKS -> TrendParams(
                startTime = now - (4 * weekMs),
                numSteps = 4,
                bucketSize = weekMs,
                formatPattern = "W'a'",
                endTime = now + weekMs
            )
            TimeFilter.LAST_6_MONTHS -> TrendParams(
                startTime = now - (6 * monthMs),
                numSteps = 6,
                bucketSize = monthMs,
                formatPattern = "MMM",
                endTime = now + monthMs
            )
            TimeFilter.LAST_YEAR -> TrendParams(
                startTime = now - (12 * monthMs),
                numSteps = 12,
                bucketSize = monthMs,
                formatPattern = "MMM",
                endTime = now + monthMs
            )
        }
    }

    private fun formatBucketStats(stats: List<com.example.scannerone.database.BucketStat>, params: TrendParams, filter: TimeFilter): List<Pair<String, Int>> {
        val app = getApplication<Application>()
        val sdf = SimpleDateFormat(params.formatPattern, Locale.getDefault())
        val result = MutableList(params.numSteps) { i -> 
            val stepStart = params.startTime + (i * params.bucketSize)
            val timeString = if (filter == TimeFilter.LAST_4_WEEKS) {
                app.getString(R.string.home_weeks_ago_short, params.numSteps - i)
            } else {
                sdf.format(Date(stepStart))
            }
            Pair(timeString, 0)
        }
        
        for (stat in stats) {
            if (stat.bucketIndex in 0 until params.numSteps) {
                val current = result[stat.bucketIndex]
                result[stat.bucketIndex] = current.copy(second = stat.count)
            }
        }
        
        return result
    }

    fun formatTimestamp(time: Long): String {
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
    }
}
