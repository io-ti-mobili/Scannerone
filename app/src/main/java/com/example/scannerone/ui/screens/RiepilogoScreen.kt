package com.example.scannerone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.WifiScanViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiepilogoScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel() // <-- 1. Inietto il ViewModel
) {
    // 2. ASCOLTO I DATI REALI DAL VIEWMODEL
    val sessions by viewModel.allSessions.collectAsState()
    val selectedId by viewModel.selectedSessionId.collectAsState()
    val networks by viewModel.sessionNetworks.collectAsState()
    val categoryData by viewModel.categoryStats.collectAsState()
    val trendData by viewModel.trendStats.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    // Colori per il grafico a torta
    val categoryColors = listOf(Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFF44336), Color(0xFF9E9E9E))

    // Calcolo UI: Testo visualizzato nel selettore
    val selectedSessionText = if (selectedId == null) {
        "Tutte le sessioni"
    } else {
        sessions.find { it.id == selectedId }?.let {
            "Sessione #${it.id} (${viewModel.formatTimestamp(it.startTime)})"
        } ?: "Tutte le sessioni"
    }

    // Calcolo UI: Distanza (Totale o della sessione singola) in km
    val displayedDistance = if (selectedId == null) {
        sessions.sumOf { it.distanceMetres } / 1000.0
    } else {
        (sessions.find { it.id == selectedId }?.distanceMetres ?: 0.0) / 1000.0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Riepilogo e Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // 1. SELETTORE SESSIONE (Dati Reali)
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
                // Opzione "Tutte"
                DropdownMenuItem(
                    text = { Text("Tutte le sessioni") },
                    onClick = {
                        viewModel.selectSession(null)
                        expanded = false
                    }
                )
                // Opzioni dal DB
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text("Sessione #${session.id} - ${viewModel.formatTimestamp(session.startTime)}") },
                        onClick = {
                            viewModel.selectSession(session.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        // 2. CARDS STATISTICHE (Dati Reali)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                title = if(selectedId == null) "Reti Totali DB" else "Reti in Sessione",
                value = "${networks.size}",
                subtitle = "Router univoci",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Distanza Percorsa",
                value = String.format(Locale.getDefault(), "%.2f km", displayedDistance),
                subtitle = if(selectedId == null) "Somma totale" else "Questa sessione",
                modifier = Modifier.weight(1f)
            )
        }

        // 3. GRAFICO A TORTA (CATEGORIE REALI)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tipologia Reti Scansionate", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                if (categoryData.isEmpty() || categoryData.values.sum() == 0f) {
                    Text("Nessuna rete trovata per questa selezione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PieChart(
                            data = categoryData.values.toList(),
                            colors = categoryColors,
                            modifier = Modifier.size(120.dp)
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        // Legenda con conteggi
                        Column {
                            categoryData.keys.forEachIndexed { index, name ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = categoryColors[index], modifier = Modifier.size(12.dp)) {}
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Mostriamo anche il numero intero
                                    val count = categoryData[name]?.toInt() ?: 0
                                    Text("$name ($count)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. GRAFICO A LINEA (ANDAMENTO TEMPORALE REALE)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Andamento Scansioni (Punti Rilevati)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                LineChart(data = trendData, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

// =======================================================
// COMPONENTI GRAFICI CUSTOM (Rimasti invariati!)
// =======================================================

@Composable
fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PieChart(data: List<Float>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = data.sum()
    if (total == 0f) return // Evita crash se non ci sono dati
    Canvas(modifier = modifier) {
        var startAngle = -90f
        for (i in data.indices) {
            val sweepAngle = (data[i] / total) * 360f
            drawArc(
                color = colors[i],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun LineChart(data: List<Int>, modifier: Modifier = Modifier) {
    if (data.isEmpty() || data.maxOrNull() == 0) return // Evita crash se non ci sono dati
    val maxVal = data.maxOrNull() ?: 1
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val xStep = width / (data.size - 1).coerceAtLeast(1)

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * xStep
            // Invertiamo l'asse Y perché 0 è in alto nel Canvas
            val y = height - ((value.toFloat() / maxVal) * height)

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            // Disegna il pallino sul punto
            drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f)
        )
    }
}