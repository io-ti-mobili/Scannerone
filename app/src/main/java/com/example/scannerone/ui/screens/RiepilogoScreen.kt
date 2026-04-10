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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiepilogoScreen(modifier: Modifier = Modifier) {
    // Simulazione dati (Questi arriveranno dal tuo ViewModel)
    val sessionOptions = listOf("Tutte le sessioni", "Oggi (10 Apr)", "Ieri (09 Apr)", "Sessione #45 (05 Apr)")
    var selectedSession by remember { mutableStateOf(sessionOptions[0]) }
    var expanded by remember { mutableStateOf(false) }

    // Dati Mock per i grafici
    val totalNetworks = 1450
    val newNetworks = 120
    val totalDistance = 45.2f // km
    val sessionDistance = 3.1f // km

    val categoryData = mapOf(
        "ISP (Tim, Vodafone...)" to 600f,
        "Fast Food" to 80f,
        "Università" to 150f,
        "Hotspot Personali" to 220f,
        "Altro" to 400f
    )
    val categoryColors = listOf(Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFF44336), Color(0xFF9E9E9E))

    // Dati per il grafico a linea (Es: reti trovate ogni 10 minuti o nei giorni scorsi)
    val trendData = listOf(10, 45, 30, 80, 120, 90, 150)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Riepilogo e Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // 1. SELETTORE SESSIONE
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedSession,
                onValueChange = {},
                readOnly = true,
                label = { Text("Seleziona Sessione") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sessionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedSession = option
                            expanded = false
                            // Qui chiamerai viewModel.loadSessionStats(option)
                        }
                    )
                }
            }
        }

        // 2. CARDS STATISTICHE
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(title = "Reti Totali", value = "$totalNetworks", subtitle = "+$newNetworks nuove", modifier = Modifier.weight(1f))
            StatCard(title = "Distanza Sessione", value = "${sessionDistance}km", subtitle = "Totale: ${totalDistance}km", modifier = Modifier.weight(1f))
        }

        // 3. GRAFICO A TORTA (CATEGORIE)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tipologia Reti Scansionate", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PieChart(data = categoryData.values.toList(), colors = categoryColors, modifier = Modifier.size(120.dp))

                    Spacer(modifier = Modifier.width(24.dp))

                    // Legenda
                    Column {
                        categoryData.keys.forEachIndexed { index, name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = categoryColors[index], modifier = Modifier.size(12.dp)) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. GRAFICO A LINEA (ANDAMENTO TEMPORALE)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Andamento Scansioni (Tempo / Reti)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                LineChart(data = trendData, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

// =======================================================
// COMPONENTI GRAFICI CUSTOM (Leggeri e Nativi)
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
    if (data.isEmpty()) return
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