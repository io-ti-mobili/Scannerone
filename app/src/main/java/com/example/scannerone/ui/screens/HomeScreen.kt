package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.HomeViewModel
import com.example.scannerone.viewmodel.HallOfFameViewModel
import com.example.scannerone.viewmodel.TimeFilter
import com.example.scannerone.ui.components.*
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(),
    hallOfFameViewModel: HallOfFameViewModel = viewModel()
) {
    val totalNets by homeViewModel.totalNetworksCount.collectAsState()
    val totalScans by homeViewModel.totalScansCount.collectAsState()
    val totalDistance by homeViewModel.totalDistance.collectAsState()
    val totalTime by homeViewModel.totalTime.collectAsState()

    val hallOfFame by hallOfFameViewModel.hallOfFame.collectAsState()
    val trendStats by homeViewModel.trendStats.collectAsState()
    val discoveryTimeFilter by homeViewModel.discoveryTimeFilter.collectAsState()
    
    val scanTrendStats by homeViewModel.scanTrendStats.collectAsState()
    val scanTimeFilter by homeViewModel.scanTimeFilter.collectAsState()
    
    val sessionTrendStats by homeViewModel.sessionTrendStats.collectAsState()
    val sessionsTimeFilter by homeViewModel.sessionsTimeFilter.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Dashboard Scanner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // --- SEZIONE NUMERI IN EVIDENZA ---
        Text("Numeri in evidenza", style = MaterialTheme.typography.titleMedium)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "Reti Uniche",
                value = "$totalNets",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Distanza",
                value = String.format(Locale.getDefault(), "%.2f km", totalDistance / 1000.0),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "Tempo Wardriving",
                value = formatTime(totalTime),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Scansioni Salvate",
                value = "$totalScans",
                icon = Icons.Default.Analytics,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SEZIONE RECORD (HALL OF FAME) ---
        Text("I Tuoi Migliori Record", style = MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val longest = hallOfFame.longestSession
            val durationTxt = longest?.let {
                val durationMs = (it.endTime ?: System.currentTimeMillis()) - it.startTime
                formatTime(durationMs)
            } ?: "-"
            val date1 = longest?.startTime?.let { hallOfFameViewModel.formatTimestamp(it) } ?: ""

            DashboardCard(
                title = "Sessione più lunga",
                value = durationTxt,
                subtitle = "In data: $date1",
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            val mostDist = hallOfFame.mostDistanceSession
            val distTxt = mostDist?.let { String.format(Locale.getDefault(), "%.2f km", it.distanceMetres / 1000.0) } ?: "-"
            val date2 = mostDist?.startTime?.let { hallOfFameViewModel.formatTimestamp(it) } ?: ""

            DashboardCard(
                title = "Sessione camminato di più",
                value = distTxt,
                subtitle = "In data: $date2",
                icon = Icons.Default.DirectionsWalk,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val mostUniq = hallOfFame.mostUniquesSession
            val date3 = mostUniq?.startTime?.let { hallOfFameViewModel.formatTimestamp(it) } ?: ""
            // Non abbiamo pre-calcolato quante reti uniche ha avuto, mettiamolo descrittivo.
            DashboardCard(
                title = "Miglior sessione per reti uniche",
                value = mostUniq?.let { "Sessione #${it.id}" } ?: "-",
                subtitle = "In data: $date3",
                icon = Icons.Default.WifiFind,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.weight(1f)) // "Come se ci fosse un quarto elemento"
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SEZIONE GRAFICO LINEARE ---
        val scanTrendStats by homeViewModel.scanTrendStats.collectAsState()
        
        var expandedMenu1 by remember { mutableStateOf(false) }
        ChartCard(
            title = "Nuove Reti nel Tempo",
            action = {
                Box {
                    OutlinedButton(
                        onClick = { expandedMenu1 = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                    ) {
                        Text(discoveryTimeFilter.label, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu1,
                        onDismissRequest = { expandedMenu1 = false }
                    ) {
                        TimeFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label, fontSize = 12.sp) },
                                onClick = { 
                                    homeViewModel.setDiscoveryTimeFilter(filter)
                                    expandedMenu1 = false 
                                }
                            )
                        }
                    }
                }
            }
        ) {
            LineChart(data = trendStats, lineColor = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        var expandedMenu2 by remember { mutableStateOf(false) }
        ChartCard(
            title = "Scansioni nel Tempo",
            action = {
                Box {
                    OutlinedButton(
                        onClick = { expandedMenu2 = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                    ) {
                        Text(scanTimeFilter.label, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu2,
                        onDismissRequest = { expandedMenu2 = false }
                    ) {
                        TimeFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label, fontSize = 12.sp) },
                                onClick = { 
                                    homeViewModel.setScanTimeFilter(filter)
                                    expandedMenu2 = false 
                                }
                            )
                        }
                    }
                }
            }
        ) {
            LineChart(data = scanTrendStats, lineColor = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        var expandedMenu3 by remember { mutableStateOf(false) }
        ChartCard(
            title = "Sessioni nel Tempo",
            action = {
                Box {
                    OutlinedButton(
                        onClick = { expandedMenu3 = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                    ) {
                        Text(sessionsTimeFilter.label, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu3,
                        onDismissRequest = { expandedMenu3 = false }
                    ) {
                        TimeFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label, fontSize = 12.sp) },
                                onClick = { 
                                    homeViewModel.setSessionsTimeFilter(filter)
                                    expandedMenu3 = false 
                                }
                            )
                        }
                    }
                }
            }
        ) {
            LineChart(data = sessionTrendStats, lineColor = MaterialTheme.colorScheme.tertiary)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}


fun formatTime(ms: Long): String {
    val durationMin = ms / 60000
    val h = durationMin / 60
    val m = durationMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
