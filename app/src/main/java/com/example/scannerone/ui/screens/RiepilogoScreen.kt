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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.R
import com.example.scannerone.viewmodel.SessionDetailsViewModel
import com.example.scannerone.ui.components.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiepilogoScreen(
    modifier: Modifier = Modifier,
    viewModel: SessionDetailsViewModel = viewModel()
) {
    val sessions by viewModel.allSessions.collectAsState(initial = emptyList())
    val selectedId by viewModel.selectedSessionId.collectAsState()
    
    val sessionUniques by viewModel.sessionUniqueNetworksCount.collectAsState()
    val sessionDiscoveries by viewModel.sessionDiscoveryCount.collectAsState()
    val sessionTotalScans by viewModel.sessionTotalScansCount.collectAsState()
    val metrics by viewModel.sessionMetrics.collectAsState()
    
    val categoryStats by viewModel.categoryStats.collectAsState()
    val securityStats by viewModel.securityStats.collectAsState()
    val frequencyStats by viewModel.frequencyStats.collectAsState()
    val sessionTrendStats by viewModel.sessionTrendStats.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    val selectedSessionText = if (selectedId == null) {
        stringResource(R.string.summary_all_sessions)
    } else {
        sessions.find { it.id == selectedId }?.let {
            stringResource(R.string.summary_session_selected_format, it.id, viewModel.formatTimestamp(it.startTime))
        } ?: stringResource(R.string.summary_all_sessions)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.summary_screen_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // SELETTORE
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedSessionText,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.summary_select_session)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.summary_all_sessions)) }, onClick = { viewModel.selectSession(null); expanded = false })
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.summary_session_item_format, session.id, viewModel.formatTimestamp(session.startTime))) },
                        onClick = { viewModel.selectSession(session.id); expanded = false }
                    )
                }
            }
        }

        // --- SEZIONE NUMERI IN EVIDENZA ---
        Text(stringResource(R.string.summary_metrics_title), style = MaterialTheme.typography.titleMedium)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = stringResource(R.string.home_unique_networks),
                value = "$sessionUniques",
                subtitle = stringResource(R.string.summary_unique_total_subtitle),
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = stringResource(R.string.summary_new_discoveries),
                value = "$sessionDiscoveries",
                subtitle = stringResource(R.string.summary_never_seen_before),
                icon = Icons.Default.Speed, // Uso Speed per indicare il "ritmo" di scoperta
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = stringResource(R.string.summary_distance_km),
                value = String.format(Locale.getDefault(), "%.2f", metrics.distanceKm),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = stringResource(R.string.summary_duration),
                value = formatTimeMin(metrics.durationMin.toLong()),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = stringResource(R.string.common_scans),
                value = "$sessionTotalScans",
                subtitle = stringResource(R.string.summary_total_executed),
                icon = Icons.Default.Analytics,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = stringResource(R.string.summary_discovery_rate_title),
                value = stringResource(R.string.summary_discovery_rate_format, metrics.discoveryRate),
                subtitle = stringResource(R.string.summary_new_networks_per_minute),
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                title = stringResource(R.string.summary_spatial_density_title),
                value = stringResource(R.string.summary_spatial_density_format, metrics.spatialDensity),
                subtitle = stringResource(R.string.summary_unique_networks_per_km),
                icon = Icons.Default.Map,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SEZIONE GRAFICI ---
        Text(stringResource(R.string.summary_network_analysis_title), style = MaterialTheme.typography.titleMedium)
        
        ChartCard(title = stringResource(R.string.common_security)) {
            PieChart(data = securityStats, isDonut = false)
        }
        
        ChartCard(title = stringResource(R.string.summary_frequencies_title)) {
            PieChart(data = frequencyStats, isDonut = true)
        }
        
        ChartCard(title = stringResource(R.string.summary_network_type_title)) {
            PieChart(data = categoryStats, isDonut = false)
        }
        
        ChartCard(title = stringResource(R.string.home_new_networks_over_time)) {
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