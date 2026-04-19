package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HallOfFameRecords(
    val longestSession: ScanSession? = null,
    val mostDistanceSession: ScanSession? = null,
    val mostUniquesSession: ScanSession? = null
)

class HallOfFameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )

    val longestSession = repository.getLongestSession()
    val mostDistanceSession = repository.getMostDistanceSession()
    val mostUniquesSession = repository.getSessionWithMostUniqueNetworks()

    val hallOfFame = combine(longestSession, mostDistanceSession, mostUniquesSession) { longest, dist, uniques ->
        HallOfFameRecords(longest, dist, uniques)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HallOfFameRecords())

    fun formatTimestamp(time: Long): String {
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
    }
}
