package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BatteryLogEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun BatteryHistoryChart(
    logs: List<BatteryLogEntity>,
    modifier: Modifier = Modifier,
    heightDp: Int = 220
) {
    if (logs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(heightDp.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Recording battery telemetry...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Sort chronologically ascending for the chart
    val sortedLogs = remember(logs) { logs.sortedBy { it.timestamp } }
    val minTime = sortedLogs.first().timestamp
    val maxTime = sortedLogs.last().timestamp
    val timeSpan = (maxTime - minTime).coerceAtLeast(60_000L) // at least 1 min

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedLog = selectedIndex?.let { if (it in sortedLogs.indices) sortedLogs[it] else null }

    val primaryLineColor = EmeraldPrimary
    val chargingColor = EmeraldLight
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip bar when touched
        if (selectedLog != null) {
            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(selectedLog.timestamp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("chart_tooltip")
            ) {
                Text(
                    text = "$dateStr • ${selectedLog.level}% • ${(selectedLog.voltageMv / 1000f)}V • ${selectedLog.temperatureDeciCelsius / 10f}°C",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = EmeraldLight,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .testTag("battery_history_chart")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sortedLogs) {
                        detectTapGestures(
                            onPress = { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetTime = minTime + (fraction * timeSpan).toLong()
                                val nearest = sortedLogs.minByOrNull { abs(it.timestamp - targetTime) }
                                selectedIndex = nearest?.let { sortedLogs.indexOf(it) }
                            }
                        )
                    }
                    .pointerInput(sortedLogs) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                val targetTime = minTime + (fraction * timeSpan).toLong()
                                val nearest = sortedLogs.minByOrNull { abs(it.timestamp - targetTime) }
                                selectedIndex = nearest?.let { sortedLogs.indexOf(it) }
                            },
                            onDragEnd = { /* keep last selected or clear */ }
                        )
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val paddingBottom = 20.dp.toPx()
                val effectiveHeight = chartHeight - paddingBottom

                // Draw Horizontal Gridlines (100%, 75%, 50%, 25%, 0%)
                val levels = listOf(100, 75, 50, 25, 0)
                for (lvl in levels) {
                    val y = effectiveHeight * (1f - (lvl / 100f))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (sortedLogs.size >= 2) {
                    val linePath = Path()
                    val fillPath = Path()

                    val firstX = ((sortedLogs.first().timestamp - minTime).toFloat() / timeSpan) * chartWidth
                    val firstY = effectiveHeight * (1f - (sortedLogs.first().level / 100f))

                    linePath.moveTo(firstX, firstY)
                    fillPath.moveTo(firstX, effectiveHeight)
                    fillPath.lineTo(firstX, firstY)

                    val points = mutableListOf<Offset>()
                    points.add(Offset(firstX, firstY))

                    for (i in 1 until sortedLogs.size) {
                        val log = sortedLogs[i]
                        val x = ((log.timestamp - minTime).toFloat() / timeSpan) * chartWidth
                        val y = effectiveHeight * (1f - (log.level / 100f))
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                        points.add(Offset(x, y))
                    }

                    fillPath.lineTo(chartWidth, effectiveHeight)
                    fillPath.close()

                    // Gradient under line
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EmeraldLight.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = effectiveHeight
                        )
                    )

                    // Line stroke
                    drawPath(
                        path = linePath,
                        color = primaryLineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw points with charging indicators
                    for (i in sortedLogs.indices) {
                        val pt = points[i]
                        val isChg = sortedLogs[i].status == 2 || sortedLogs[i].pluggedType != 0
                        drawCircle(
                            color = if (isChg) chargingColor else primaryLineColor,
                            radius = if (isChg) 4.5.dp.toPx() else 2.5.dp.toPx(),
                            center = pt
                        )
                    }

                    // Vertical cursor if selected
                    if (selectedIndex != null && selectedIndex!! in points.indices) {
                        val selPt = points[selectedIndex!!]
                        drawLine(
                            color = CyanAccent,
                            start = Offset(selPt.x, 0f),
                            end = Offset(selPt.x, effectiveHeight),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(
                            color = CyanAccent,
                            radius = 6.dp.toPx(),
                            center = selPt
                        )
                    }
                }
            }
        }
    }
}
