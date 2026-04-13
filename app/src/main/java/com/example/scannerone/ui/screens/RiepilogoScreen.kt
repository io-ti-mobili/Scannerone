package com.example.scannerone.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.viewmodel.WifiScanViewModel
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiepilogoScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel()
) {
    val sessions by viewModel.allSessions.collectAsState(initial = emptyList<ScanSession>())
    val selectedId by viewModel.selectedSessionId.collectAsState()
    val networks by viewModel.sessionNetworks.collectAsState()
    val categoryData by viewModel.categoryStats.collectAsState()
    val trendData by viewModel.trendStats.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    val categoryColors = listOf(
        Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFF44336), Color(0xFF9E9E9E)
    )

    val selectedSessionText = if (selectedId == null) "Tutte le sessioni"
    else sessions.find { it.id == selectedId }?.let { "Sessione #${it.id} (${viewModel.formatTimestamp(it.startTime)})" } ?: "Tutte le sessioni"

    val displayedDistance = if (selectedId == null) {
        sessions.sumOf { it.distanceMetres } / 1000.0
    } else {
        (sessions.find { it.id == selectedId }?.distanceMetres ?: 0.0) / 1000.0
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Dashboard Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // SELETTORE
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedSessionText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Filtra per Sessione") },
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

        // CARDS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(title = if(selectedId == null) "Reti Totali DB" else "Reti in Sessione", value = "${networks.size}", subtitle = "Router univoci", modifier = Modifier.weight(1f))
            StatCard(title = "Distanza Percorsa", value = String.format(Locale.getDefault(), "%.2f km", displayedDistance), subtitle = if(selectedId == null) "Somma totale" else "Questa sessione", modifier = Modifier.weight(1f))
        }

        // GRAFICO A TORTA
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tipologia Reti Scansionate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))

                if (categoryData.isEmpty() || categoryData.values.sum() == 0f) {
                    Text("Nessuna rete trovata per questa selezione.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                } else {
                    ChartJsPieChart(
                        data = categoryData.values.toList(),
                        labels = categoryData.keys.toList(),
                        colors = categoryColors,
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // LEGENDA CON NUMERI
                    FlowRow(mainAxisSpacing = 12.dp, crossAxisSpacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                        categoryData.keys.toList().forEachIndexed { index, name ->
                            val count = categoryData.values.toList()[index].toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = categoryColors[index], modifier = Modifier.size(10.dp)) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                // TESTO CON NOME E NUMERO
                                Text("$name ($count)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // GRAFICO A LINEA (TEMPO / RETI)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Andamento Scoperta Reti (Tempo/Quantità)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                ChartJsLineChart(
                    data = trendData,
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 10.dp)
                )
            }
        }
    }
}

// ... UI DI SUPPORTO (StatCard e FlowRow rimangono invariati rispetto al precedente) ...

@Composable
fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: Dp = 0.dp,
    crossAxisSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val spacingPx = mainAxisSpacing.roundToPx()
        val crossSpacingPx = crossAxisSpacing.roundToPx()

        val lines = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val lineHeights = mutableListOf<Int>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0
        var currentLineHeight = 0

        placeholders.forEach { placeable ->
            if (currentLineWidth + placeable.width > constraints.maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                lineHeights.add(currentLineHeight)
                currentLine = mutableListOf()
                currentLineWidth = 0
                currentLineHeight = 0
            }
            currentLine.add(placeable)
            currentLineWidth += placeable.width + spacingPx
            currentLineHeight = maxOf(currentLineHeight, placeable.height)
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
            lineHeights.add(currentLineHeight)
        }

        val width = constraints.maxWidth
        val height = lineHeights.sum() + (lines.size - 1).coerceAtLeast(0) * crossSpacingPx

        layout(width, height) {
            var y = 0
            lines.forEachIndexed { lineIdx, line ->
                var x = 0
                line.forEach { placeable ->
                    placeable.place(x, y + (lineHeights[lineIdx] - placeable.height) / 2)
                    x += placeable.width + spacingPx
                }
                y += lineHeights[lineIdx] + crossSpacingPx
            }
        }
    }
}


// ==================================================================================
// GRAFICI AVANZATI INTERATTIVI
// ==================================================================================

@Composable
fun ChartJsPieChart(data: List<Float>, labels: List<String>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = data.sum()
    if (total == 0f) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val textColor = MaterialTheme.colorScheme.onSurface
    val bubbleColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)

    Box(contentAlignment = Alignment.Center, modifier = modifier) {

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(data) {
            detectTapGestures { offset ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val distance = hypot(offset.x - center.x, offset.y - center.y)

                // Essendo una torta piena, rileva il click ovunque dentro il cerchio
                if (distance <= size.width / 2f) {
                    var angle = (atan2(offset.y - center.y, offset.x - center.x) * 180 / PI).toFloat()
                    if (angle < 0) angle += 360f
                    angle = (angle + 90) % 360f

                    var currentAngle = 0f
                    data.forEachIndexed { index, value ->
                        val sweep = (value / total) * 360f
                        if (angle in currentAngle..(currentAngle + sweep)) {
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                        currentAngle += sweep
                    }
                } else {
                    selectedIndex = null
                }
            }
        }) {
            var startAngle = -90f
            data.forEachIndexed { index, value ->
                val sweep = (value / total) * 360f
                val isSelected = selectedIndex == index

                // TORTA PIENA: useCenter = true e rimozione di "style = Stroke"
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    size = if (isSelected) size * 1.08f else size,
                    topLeft = if (isSelected) Offset(-size.width * 0.04f, -size.height * 0.04f) else Offset.Zero
                )
                startAngle += sweep
            }
        }

        // FUMETTO CENTRALE
        if (selectedIndex != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(6.dp),
                shape = RoundedCornerShape(CornerSize(16.dp)),
                modifier = Modifier.widthIn(max = 140.dp).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = labels[selectedIndex!!],
                        style = MaterialTheme.typography.labelSmall,
                        color = colors[selectedIndex!!],
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${data[selectedIndex!!].toInt()} Router",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                    Text(
                        text = "${((data[selectedIndex!!]/total)*100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ChartJsLineChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { Text("Dati insufficienti") }
        return
    }

    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    val axisTextPaint = remember(onSurface) {
        Paint().apply {
            color = onSurface.toArgb()
            textSize = density.run { 10.sp.toPx() }
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
    }


    val maxVal = (data.maxOfOrNull { it.second } ?: 1).toFloat()
    val steps = minOf(4, maxVal.toInt()).coerceAtLeast(1)

    val yAxisWidth = density.run { 40.dp.toPx() }
    val xAxisHeight = density.run { 20.dp.toPx() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(data) {
            detectTapGestures { offset ->
                if (offset.x >= yAxisWidth && offset.y <= size.height - xAxisHeight) {
                    val chartWidth = size.width - yAxisWidth
                    val xStep = chartWidth / (data.size - 1).coerceAtLeast(1)
                    val chartX = offset.x - yAxisWidth
                    val idx = (chartX / xStep).roundToInt().coerceIn(0, data.size - 1)
                    selectedIdx = idx

                    val pointX = yAxisWidth + (idx * xStep)
                    val chartHeight = size.height - xAxisHeight
                    val pointY = chartHeight - (data[idx].second / maxVal * chartHeight)
                    tooltipOffset = Offset(pointX, pointY)
                } else {
                    selectedIdx = null
                }
            }
        }) {
            val chartWidth = size.width - yAxisWidth
            val chartHeight = size.height - xAxisHeight

            if (data.size < 2) return@Canvas
            val xStep = chartWidth / (data.size - 1)

            // DISEGNO ASSI E GRIGLIA
            val uniqueLabels = mutableSetOf<Int>()
            for (i in 0..steps) {
                val y = chartHeight - (chartHeight / steps * i)
                val labelVal = (maxVal / steps * i).toInt()

                // Evita di stampare due volte lo stesso numero sull'asse Y
                if (uniqueLabels.add(labelVal)) {
                    drawLine(
                        color = gridColor.copy(alpha = if(i==0) 1f else 0.4f),
                        start = Offset(yAxisWidth, y),
                        end = Offset(size.width, y),
                        strokeWidth = (if(i==0) 2.dp else 1.dp).toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        labelVal.toString(),
                        yAxisWidth - 8.dp.toPx(),
                        y + 4.dp.toPx(),
                        axisTextPaint
                    )
                }
            }

            drawLine(gridColor, Offset(yAxisWidth, chartHeight), Offset(size.width, chartHeight), 2.dp.toPx())

            // DISEGNO TRACCIATO
            val path = Path()
            data.forEachIndexed { i, pair ->
                val x = yAxisWidth + (i * xStep)
                val y = chartHeight - (pair.second / maxVal * chartHeight)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val fillPath = Path().apply {
                addPath(path)
                lineTo(yAxisWidth + chartWidth, chartHeight)
                lineTo(yAxisWidth, chartHeight)
                close()
            }

            drawPath(fillPath, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)))
            drawPath(path, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            // RIFERIMENTI SELEZIONE
            data.forEachIndexed { i, pair ->
                val x = yAxisWidth + (i * xStep)
                val y = chartHeight - (pair.second / maxVal * chartHeight)

                drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
                drawCircle(primaryColor, 3.dp.toPx(), Offset(x, y), style = Stroke(width = 2.dp.toPx()))

                if (i == selectedIdx) {
                    drawLine(primaryColor.copy(0.5f), Offset(x, 0f), Offset(x, chartHeight), 1.dp.toPx())
                    drawCircle(primaryColor, 6.dp.toPx(), Offset(x, y))
                    drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
                }
            }
        }

        // FUMETTO FLUTTUANTE (SOLO TEMPO E NUMERO)
        if (selectedIdx != null) {
            TooltipBubble(
                title = "Tempo: ${data[selectedIdx!!].first}", // X
                value = "Reti: ${data[selectedIdx!!].second}", // Y
                color = primaryColor,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(
                        x = with(density) { tooltipOffset.x.toDp() - 55.dp },
                        y = with(density) { tooltipOffset.y.toDp() - 70.dp }
                    )
            )
        }
    }
}

@Composable
fun TooltipBubble(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(8.dp).copy(bottomStart = CornerSize(8.dp), bottomEnd = CornerSize(8.dp)),
        modifier = modifier.widthIn(min = 110.dp).height(55.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = color)
        }
    }
}