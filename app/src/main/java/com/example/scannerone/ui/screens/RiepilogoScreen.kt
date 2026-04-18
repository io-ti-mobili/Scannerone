package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.DashboardViewModel
import com.example.scannerone.ui.components.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiepilogoScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val sessions by viewModel.allSessions.collectAsState(initial = emptyList())
    val selectedId by viewModel.selectedSessionId.collectAsState()
    
    val sessionUniques by viewModel.sessionUniqueNetworksCount.collectAsState()
    val sessionTotalScans by viewModel.sessionTotalScansCount.collectAsState()
    val metrics by viewModel.sessionMetrics.collectAsState()
    
    val categoryStats by viewModel.categoryStats.collectAsState()
    val securityStats by viewModel.securityStats.collectAsState()
    val frequencyStats by viewModel.frequencyStats.collectAsState()
    val sessionTrendStats by viewModel.sessionTrendStats.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    val selectedSessionText = if (selectedId == null) "Tutte le sessioni"
    else sessions.find { it.id == selectedId }?.let { "Sessione #${it.id} (${viewModel.formatTimestamp(it.startTime)})" } ?: "Tutte le sessioni"

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Riepilogo Sessioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // SELETTORE
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedSessionText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Seleziona Sessione") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Tutte le sessioni") }, onClick = { viewModel.selectSession(null); expanded = false })
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text("Sessione #${session.id} - ${viewModel.formatTimestamp(session.startTime)}") },
                        onClick = { viewModel.selectSession(session.id); expanded = false }
                    )
                }
            }
        }

        // --- SEZIONE NUMERI IN EVIDENZA ---
        Text("Metriche della Sessione", style = MaterialTheme.typography.titleMedium)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "Reti Uniche",
                value = "$sessionUniques",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Distanza (km)",
                value = String.format(Locale.getDefault(), "%.2f", metrics.distanceKm),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "Durata",
                value = formatTimeMin(metrics.durationMin.toLong()),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Scansioni Effettuate",
                value = "$sessionTotalScans",
                icon = Icons.Default.Analytics,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = "Discovery Rate",
                value = String.format(Locale.getDefault(), "%.1f /min", metrics.discoveryRate),
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Densità Spaziale",
                value = String.format(Locale.getDefault(), "%.0f /km", metrics.spatialDensity),
                icon = Icons.Default.Map,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SEZIONE GRAFICI ---
        Text("Analisi Reti", style = MaterialTheme.typography.titleMedium)
        
        ChartCard(title = "Sicurezza (capabilities)") {
            PieChart(data = securityStats, isDonut = false)
        }
        
        ChartCard(title = "Frequenze") {
            PieChart(data = frequencyStats, isDonut = true)
        }
        
        ChartCard(title = "Tipologia di Rete (SSID)") {
            PieChart(data = categoryStats, isDonut = false)
        }
        
        ChartCard(title = "Andamento Scoperta (diviso in 7)") {
            LineChart(data = sessionTrendStats, lineColor = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun formatTimeMin(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}