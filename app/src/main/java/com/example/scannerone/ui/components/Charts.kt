package com.example.scannerone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.sqrt
import androidx.compose.ui.graphics.vector.ImageVector

data class TooltipData(val pos: Offset, val label: String, val value: String, val sub: String = "")

@Composable
fun ChartTooltip(data: TooltipData, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val cardW = 130.dp
    val cardH = if (data.sub.isNotEmpty()) 62.dp else 44.dp
    val tailH = 8.dp

    val xDp = with(density) { data.pos.x.toDp() } - cardW / 2
    val yDp = with(density) { data.pos.y.toDp() } - cardH - tailH

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Box(modifier = Modifier.absoluteOffset(x = xDp, y = yDp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier.width(cardW),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = data.label,
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = data.value,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (data.sub.isNotEmpty()) {
                            Text(
                                text = data.sub,
                                color = Color(0xFF78909C),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Canvas(modifier = Modifier.size(16.dp, tailH)) {
                    val p = Path().apply {
                        moveTo(size.width / 2f, size.height)
                        lineTo(0f, 0f)
                        lineTo(size.width, 0f)
                        close()
                    }
                    drawPath(p, Color(0xFF1A1A2E))
                }
            }
        }
    }
}

@Composable
fun ChartCard(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (action != null) action()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun PieChart(
    data: Map<String, Float>,
    isDonut: Boolean = false,
    colors: List<Color> = listOf(
        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
        Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF757575)
    )
) {
    if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("Dati insufficienti")
        }
        return
    }
    
    val total = data.values.sum()
    var clickedTooltip by remember { mutableStateOf<TooltipData?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(140.dp).padding(12.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(data) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val distance = sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                    val radius = size.width / 2f
                    
                    if (distance <= radius && (!isDonut || distance >= radius - (size.width / 4f))) {
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        
                        var cumulativeAngle = 270f
                        for (entry in data.entries) {
                            val sweepAngle = (entry.value / total) * 360f
                            val start = cumulativeAngle % 360f
                            val end = (cumulativeAngle + sweepAngle) % 360f
                            
                            val inRange = if (start < end) {
                                angle in start..end
                            } else {
                                angle >= start || angle <= end
                            }
                            
                            if (inRange) {
                                val pct = (entry.value / total * 100).toInt()
                                clickedTooltip = TooltipData(
                                    pos = offset,
                                    label = entry.key,
                                    value = "${entry.value.toInt()} reti",
                                    sub = "$pct% del totale"
                                )
                                break
                            }
                            cumulativeAngle += sweepAngle
                        }
                    } else {
                        clickedTooltip = null
                    }
                }
            }) {
                var startAngle = -90f
                data.values.forEachIndexed { index, value ->
                    val sweepAngle = (value / total) * 360f
                    val color = colors[index % colors.size]
                    if (isDonut) {
                        drawArc(color, startAngle, sweepAngle, useCenter = false, style = Stroke(width = size.width / 4))
                    } else {
                        drawArc(color, startAngle, sweepAngle, useCenter = true)
                    }
                    startAngle += sweepAngle
                }
            }
            
            clickedTooltip?.let { td ->
                ChartTooltip(data = td, onDismiss = { clickedTooltip = null })
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(entry.key, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(
                            "${(entry.value / total * 100).toInt()}% (${entry.value.toInt()})", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty() || data.size < 2) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("Dati insufficienti (almeno 2 punti)")
        }
        return
    }
    
    val maxVal = data.maxOf { it.second }.toFloat().coerceAtLeast(5f)
    var clickedTooltip by remember { mutableStateOf<TooltipData?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            // Y-axis
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(end = 8.dp)) {
                for (i in 4 downTo 0) {
                    val labelValue = (maxVal * (i / 4f)).toInt()
                    Text("$labelValue", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp)) {
                Canvas(modifier = Modifier.fillMaxSize().pointerInput(data) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val stepX = width / (data.size - 1).coerceAtLeast(1)
                    
                    var closestIndex = -1
                    var minDistance = Float.MAX_VALUE
                    
                    data.forEachIndexed { index, pair ->
                        val x = index * stepX
                        val y = height - ((pair.second / maxVal) * height)
                        val dx = offset.x - x
                        val dy = offset.y - y
                        val distance = sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                        
                        if (distance < minDistance) {
                            minDistance = distance
                            closestIndex = index
                        }
                    }
                    
                    if (minDistance < 70f && closestIndex != -1) {
                        val pt = data[closestIndex]
                        clickedTooltip = TooltipData(
                            pos = offset,
                            label = pt.first,
                            value = "${pt.second} nuove reti"
                        )
                    } else {
                        clickedTooltip = null
                    }
                }
            }) {
                val width = size.width
                val height = size.height
                val stepX = width / (data.size - 1).coerceAtLeast(1)
                
                val path = Path()
                
                // Draw grid lines (5 lines)
                for (i in 0..4) {
                    val yLine = height * (i / 4f)
                    drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, yLine), Offset(width, yLine), strokeWidth = 2f)
                }
                
                data.forEachIndexed { index, pair ->
                    val x = index * stepX
                    val y = height - ((pair.second / maxVal) * height)
                    
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                
                drawPath(path, color = lineColor, style = Stroke(width = 6f))
                
                // Draw points
                data.forEachIndexed { index, pair ->
                    val x = index * stepX
                    val y = height - ((pair.second / maxVal) * height)
                    drawCircle(color = lineColor, radius = 8f, center = Offset(x,y))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(x,y))
                }
            }
            
            clickedTooltip?.let { td ->
                ChartTooltip(data = td, onDismiss = { clickedTooltip = null })
            }
            }
        }
        
        // X-axis: at most 5 evenly-spaced labels
        val maxLabels = 5
        val xLabels: List<String> = if (data.size <= maxLabels) {
            data.map { formatXLabel(it.first) }
        } else {
            // pick indices evenly spread across [0, data.size-1]
            val step = (data.size - 1).toDouble() / (maxLabels - 1)
            (0 until maxLabels).map { i ->
                val idx = (i * step).toInt().coerceIn(0, data.size - 1)
                formatXLabel(data[idx].first)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach {
                Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Formatta una label dell'asse X:
 *  - Se contiene un orario (HH:MM o HH:MM ...), lo arrotonda a HH:00.
 *  - Altrimenti restituisce la label invariata.
 */
private fun formatXLabel(raw: String): String {
    // Regex: cerca pattern HH:MM (con cifre alle 0-posizione)
    val timeRegex = Regex("""\b(\d{1,2}):(\d{2})\b""")
    return timeRegex.replace(raw) { mr ->
        val hour = mr.groupValues[1].padStart(2, '0')
        "$hour:00"
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = contentColor            // icona sempre identica al testo, non separata
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icona: stesso colore del testo, opacità 64% — presente ma discreta
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor.copy(alpha = 0.64f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                // Titolo: leggero, 60% opacità
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.60f),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            // Valore: piena opacità, bold — è il dato principale
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                // Sottotitolo: 45% opacità — contestuale, non invadente
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.45f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
