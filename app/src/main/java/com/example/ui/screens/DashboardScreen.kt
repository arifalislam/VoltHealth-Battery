package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BatteryHealthReport
import com.example.data.model.ChargeSessionEntity
import com.example.data.model.LiveBatteryState
import com.example.ui.components.BatteryGauge
import com.example.ui.components.MetricCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun DashboardScreen(
    liveState: LiveBatteryState,
    healthReport: BatteryHealthReport,
    lastSession: ChargeSessionEntity?,
    onNavigateToHealth: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VoltHealth",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${liveState.pluggedSource} • ${liveState.technology}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (liveState.isCharging) EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (liveState.isCharging) EmeraldLight else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (liveState.isCharging) Icons.Default.Bolt else Icons.Default.Power,
                        contentDescription = null,
                        tint = if (liveState.isCharging) EmeraldLight else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = liveState.statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (liveState.isCharging) EmeraldLight else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Battery Dial & Real-Time Power Telemetry
        BatteryGauge(
            level = liveState.level,
            isCharging = liveState.isCharging,
            powerWatts = liveState.powerWatts,
            currentMa = liveState.currentMa,
            size = 230.dp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Time Remaining Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("time_remaining_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (liveState.isCharging) "TIME UNTIL FULL" else "ESTIMATED RUNTIME",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val hours = liveState.estimatedRemainingHours ?: 0f
                    val h = hours.toInt()
                    val m = ((hours - h) * 60).toInt()
                    Text(
                        text = if (hours <= 0f && liveState.isFull) "Fully Charged"
                        else if (h > 0) "${h}h ${m}m"
                        else "${m}m",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (liveState.isCharging) EmeraldLight else CyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Drain / Charge Speed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${liveState.dischargeRatePercentPerHour}% / hr",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Last Charged Time & Duration Card (Prominent requirement)
        LastChargedCard(
            lastSession = lastSession,
            isCurrentlyCharging = liveState.isCharging
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Grid (Power Watts, Voltage, Current, Temperature)
        Text(
            text = "LIVE POWER METRICS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Power Usage",
                value = String.format(Locale.US, "%.2f", liveState.powerWatts),
                unit = "W",
                subtitle = "${(liveState.powerWatts * 1000).toInt()} mW active load",
                icon = Icons.Default.Bolt,
                iconTint = CyanAccent,
                testTag = "metric_card_power",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Current Flow",
                value = "${abs(liveState.currentMa).toInt()}",
                unit = "mA",
                subtitle = if (liveState.currentMa >= 0) "Charging rate" else "Discharge rate",
                icon = Icons.Default.ElectricMeter,
                iconTint = if (liveState.currentMa >= 0) EmeraldLight else AmberWarning,
                testTag = "metric_card_current",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Voltage",
                value = String.format(Locale.US, "%.3f", liveState.voltageVolts),
                unit = "V",
                subtitle = "${liveState.voltageMv} mV level",
                icon = Icons.Default.Speed,
                iconTint = EmeraldPrimary,
                testTag = "metric_card_voltage",
                modifier = Modifier.weight(1f)
            )
            val tempColor = when {
                liveState.temperatureCelsius > 40f -> CrimsonAlert
                liveState.temperatureCelsius > 35f -> AmberWarning
                else -> EmeraldLight
            }
            MetricCard(
                title = "Temperature",
                value = String.format(Locale.US, "%.1f", liveState.temperatureCelsius),
                unit = "°C",
                subtitle = if (liveState.temperatureCelsius > 38f) "Warm battery" else "Optimal thermal state",
                icon = Icons.Default.Thermostat,
                iconTint = tempColor,
                testTag = "metric_card_temp",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Health Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("health_summary_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = EmeraldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Battery Health & Capacity",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${healthReport.healthPercentage}% health • ${healthReport.estimatedTotalCapacityMah.toInt()} mAh capacity",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldLight
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToHealth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("button_view_health")
                    ) {
                        Text("Details")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (healthReport.healthPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = EmeraldLight,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LastChargedCard(
    lastSession: ChargeSessionEntity?,
    isCurrentlyCharging: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("last_charged_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Charge history",
                        tint = EmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAST CHARGED SESSION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCurrentlyCharging) {
                    Surface(
                        color = EmeraldLight.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "CHARGING NOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = EmeraldLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (lastSession != null && lastSession.durationMs > 0) {
                val durationMin = (lastSession.durationMs / 60_000L).toInt()
                val durationHrs = durationMin / 60
                val durationRemMin = durationMin % 60
                val durationStr = if (durationHrs > 0) "${durationHrs}h ${durationRemMin}m" else "${durationMin}m"

                val timeEnded = lastSession.endTime ?: lastSession.startTime
                val timeDiff = System.currentTimeMillis() - timeEnded
                val agoHrs = (timeDiff / 3_600_000L).toInt()
                val agoMin = ((timeDiff % 3_600_000L) / 60_000L).toInt()
                val timeAgoStr = when {
                    agoHrs > 24 -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timeEnded))
                    agoHrs > 0 -> "$agoHrs hours ago"
                    agoMin > 0 -> "$agoMin minutes ago"
                    else -> "Just now"
                }

                val gained = lastSession.endLevel - lastSession.startLevel

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Last Charged Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = timeAgoStr,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeEnded)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Charge Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyanAccent
                        )
                        Text(
                            text = "Active plugged",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Level Gained",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+$gained%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldLight
                        )
                        Text(
                            text = "${lastSession.startLevel}% → ${lastSession.endLevel}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect to charger to begin recording session time and duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
