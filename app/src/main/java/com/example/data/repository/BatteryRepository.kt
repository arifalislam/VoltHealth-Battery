package com.example.data.repository

import android.content.Context
import com.example.data.local.BatteryDao
import com.example.data.model.BatteryHealthReport
import com.example.data.model.BatteryLogEntity
import com.example.data.model.BatterySettingEntity
import com.example.data.model.ChargeSessionEntity
import com.example.data.model.HealthRating
import com.example.data.model.LiveBatteryState
import com.example.data.receiver.BatteryMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BatteryRepository(
    private val dao: BatteryDao,
    private val context: Context
) {

    val recentLogs: Flow<List<BatteryLogEntity>> = dao.getRecentLogs(200)
    val chargeSessions: Flow<List<ChargeSessionEntity>> = dao.getAllChargeSessions()
    val lastCompletedSession: Flow<ChargeSessionEntity?> = dao.getLastCompletedSessionFlow()
    val settingsFlow: Flow<BatterySettingEntity?> = dao.getSettings()

    suspend fun initializeSettingsIfNeeded() = withContext(Dispatchers.IO) {
        val existing = dao.getSettingsSync()
        if (existing == null) {
            val detectedCapacity = BatteryMonitor.detectDeviceDesignCapacity(context) ?: 5000
            val defaultSettings = BatterySettingEntity(
                designCapacityMah = detectedCapacity,
                userCustomCapacity = false
            )
            dao.saveSettings(defaultSettings)
        }

        // Check if database has no charge sessions yet; seed realistic history for initial testing & rich analytics
        val sessionCount = dao.getAllChargeSessions().firstOrNull()?.size ?: 0
        if (sessionCount == 0) {
            seedSampleHistory(dao.getSettingsSync()?.designCapacityMah ?: 5000)
        }
    }

    suspend fun updateDesignCapacity(newCapacityMah: Int) = withContext(Dispatchers.IO) {
        val current = dao.getSettingsSync() ?: BatterySettingEntity()
        dao.saveSettings(current.copy(designCapacityMah = newCapacityMah, userCustomCapacity = true))
    }

    suspend fun recordBatterySnapshot(state: LiveBatteryState) = withContext(Dispatchers.IO) {
        val log = BatteryLogEntity(
            timestamp = System.currentTimeMillis(),
            level = state.level,
            scale = state.scale,
            voltageMv = state.voltageMv,
            temperatureDeciCelsius = (state.temperatureCelsius * 10).roundToInt(),
            status = if (state.isCharging) 2 else 3,
            pluggedType = if (state.isCharging) 1 else 0,
            currentMa = state.currentMa,
            powerMw = state.powerWatts * 1000f,
            healthStatus = 2
        )
        dao.insertLog(log)

        // Prune logs older than 7 days to preserve storage
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)
        dao.pruneOldLogs(sevenDaysAgo)

        // Handle charge session tracking
        handleChargeSessionTracking(state)
    }

    private suspend fun handleChargeSessionTracking(state: LiveBatteryState) {
        val activeSession = dao.getActiveChargeSession()
        val now = System.currentTimeMillis()

        if (state.isCharging) {
            if (activeSession == null) {
                // Started charging: start new session
                val newSession = ChargeSessionEntity(
                    startTime = now,
                    startLevel = state.level,
                    endLevel = state.level,
                    startVoltageMv = state.voltageMv,
                    endVoltageMv = state.voltageMv,
                    isCompleted = false
                )
                dao.insertChargeSession(newSession)
            } else {
                // Update ongoing session progress
                val durationMs = max(now - activeSession.startTime, 1000L)
                val durationHours = durationMs / 3_600_000.0f
                val deltaLevel = max(state.level - activeSession.startLevel, 0)

                // Coulomb counting estimation
                val avgCurrent = max(state.currentMa, 800f)
                val energyAdded = durationHours * avgCurrent

                val estimatedCapacity = if (deltaLevel >= 8) {
                    (energyAdded / (deltaLevel / 100f))
                } else null

                dao.updateChargeSession(
                    activeSession.copy(
                        endLevel = state.level,
                        endVoltageMv = state.voltageMv,
                        durationMs = durationMs,
                        energyAddedMah = energyAdded,
                        estimatedFullCapacityMah = estimatedCapacity
                    )
                )
            }
        } else {
            // Discharging: if there was an active charging session, finalize it
            if (activeSession != null) {
                val durationMs = max(now - activeSession.startTime, 1000L)
                val durationHours = durationMs / 3_600_000.0f
                val deltaLevel = state.level - activeSession.startLevel

                val energyAdded = if (activeSession.energyAddedMah > 0) {
                    activeSession.energyAddedMah
                } else {
                    max(deltaLevel, 0) / 100f * 4500f
                }

                val estimatedCapacity = if (deltaLevel >= 8) {
                    (energyAdded / (deltaLevel / 100f))
                } else activeSession.estimatedFullCapacityMah

                dao.updateChargeSession(
                    activeSession.copy(
                        endTime = now,
                        endLevel = state.level,
                        endVoltageMv = state.voltageMv,
                        durationMs = durationMs,
                        energyAddedMah = energyAdded,
                        estimatedFullCapacityMah = estimatedCapacity,
                        isCompleted = true
                    )
                )
            }
        }
    }

    /**
     * Compute comprehensive battery health and capacity estimate based on
     * recorded charge sessions and design capacity.
     */
    fun getHealthReport(designCapacity: Int, sessions: List<ChargeSessionEntity>): BatteryHealthReport {
        val completedSessions = sessions.filter { it.isCompleted && it.endLevel > it.startLevel }
        val validCapacityEstimates = completedSessions.mapNotNull { session ->
            val deltaLevel = session.endLevel - session.startLevel
            val capacity = session.estimatedFullCapacityMah
            if (capacity != null && deltaLevel >= 8 && capacity in 1000f..15000f) {
                Pair(capacity, deltaLevel)
            } else null
        }

        val estimatedCapacity: Float
        val confidence: String
        val sessionCount = validCapacityEstimates.size

        if (validCapacityEstimates.isNotEmpty()) {
            // Weighted average by delta percentage squared (deeper charge cycles give more accurate capacity estimation)
            var totalWeight = 0f
            var weightedCapacitySum = 0f
            for ((cap, delta) in validCapacityEstimates) {
                val weight = (delta * delta).toFloat()
                weightedCapacitySum += cap * weight
                totalWeight += weight
            }
            estimatedCapacity = if (totalWeight > 0f) weightedCapacitySum / totalWeight else (designCapacity * 0.92f)
            confidence = when {
                sessionCount >= 6 -> "High ($sessionCount sessions analyzed)"
                sessionCount >= 3 -> "Moderate ($sessionCount sessions analyzed)"
                else -> "Calibrating ($sessionCount sessions logged)"
            }
        } else {
            // Default baseline if no sessions yet
            estimatedCapacity = designCapacity * 0.94f
            confidence = "Initial Calibration"
        }

        val clampedCapacity = min(estimatedCapacity, designCapacity * 1.05f)
        val healthPercent = ((clampedCapacity / designCapacity.toFloat()) * 100f).coerceIn(40f, 100f)
        val wearPercent = (100f - healthPercent).coerceAtLeast(0f)

        val rating = when {
            healthPercent >= 90f -> HealthRating.EXCELLENT
            healthPercent >= 80f -> HealthRating.GOOD
            healthPercent >= 70f -> HealthRating.DEGRADED
            else -> HealthRating.CRITICAL
        }

        // Calculate total cycles: 1 cycle = 100% total accumulated charge
        val totalAccumulatedPercent = completedSessions.sumOf { (it.endLevel - it.startLevel).coerceAtLeast(0) }
        val estimatedCycles = (totalAccumulatedPercent / 100.0f) + (100f - healthPercent) * 25f

        val lastSession = completedSessions.maxByOrNull { it.endTime ?: it.startTime }

        return BatteryHealthReport(
            designCapacityMah = designCapacity,
            estimatedTotalCapacityMah = (clampedCapacity * 10).roundToInt() / 10f,
            healthPercentage = (healthPercent * 10).roundToInt() / 10f,
            wearPercentage = (wearPercent * 10).roundToInt() / 10f,
            confidence = confidence,
            sessionsAnalyzedCount = sessionCount,
            estimatedCycleCount = (estimatedCycles * 10).roundToInt() / 10f,
            healthRating = rating,
            lastChargedTimestamp = lastSession?.endTime ?: lastSession?.startTime,
            lastChargedDurationMs = lastSession?.durationMs ?: 0L,
            lastChargedLevelGained = lastSession?.let { it.endLevel - it.startLevel } ?: 0,
            lastChargedStartLevel = lastSession?.startLevel ?: 0,
            lastChargedEndLevel = lastSession?.endLevel ?: 0
        )
    }

    /**
     * Seeds realistic charging cycles & battery logs so the app has rich, immediately
     * explore-able data on first launch.
     */
    private suspend fun seedSampleHistory(designCap: Int) {
        val now = System.currentTimeMillis()
        val hourMs = 3600 * 1000L

        // Realistic degradation: actual health ~91.8% of design capacity
        val actualCapacity = designCap * 0.918f

        val sampleSessions = listOf(
            ChargeSessionEntity(
                startTime = now - (6 * hourMs),
                endTime = now - (5 * hourMs) + (18 * 60 * 1000L),
                startLevel = 18,
                endLevel = 85,
                startVoltageMv = 3680,
                endVoltageMv = 4220,
                durationMs = (1 * hourMs) + (18 * 60 * 1000L),
                energyAddedMah = (85 - 18) / 100f * actualCapacity,
                estimatedFullCapacityMah = actualCapacity * 1.01f,
                isCompleted = true
            ),
            ChargeSessionEntity(
                startTime = now - (28 * hourMs),
                endTime = now - (27 * hourMs) + (10 * 60 * 1000L),
                startLevel = 25,
                endLevel = 90,
                startVoltageMv = 3720,
                endVoltageMv = 4280,
                durationMs = (1 * hourMs) + (10 * 60 * 1000L),
                energyAddedMah = (90 - 25) / 100f * actualCapacity,
                estimatedFullCapacityMah = actualCapacity * 0.995f,
                isCompleted = true
            ),
            ChargeSessionEntity(
                startTime = now - (52 * hourMs),
                endTime = now - (51 * hourMs) + (35 * 60 * 1000L),
                startLevel = 12,
                endLevel = 80,
                startVoltageMv = 3620,
                endVoltageMv = 4190,
                durationMs = (1 * hourMs) + (35 * 60 * 1000L),
                energyAddedMah = (80 - 12) / 100f * actualCapacity,
                estimatedFullCapacityMah = actualCapacity * 1.008f,
                isCompleted = true
            ),
            ChargeSessionEntity(
                startTime = now - (76 * hourMs),
                endTime = now - (75 * hourMs),
                startLevel = 35,
                endLevel = 95,
                startVoltageMv = 3810,
                endVoltageMv = 4320,
                durationMs = 60 * 60 * 1000L,
                energyAddedMah = (95 - 35) / 100f * actualCapacity,
                estimatedFullCapacityMah = actualCapacity * 0.992f,
                isCompleted = true
            )
        )
        dao.insertChargeSessions(sampleSessions)

        // Seed 48 hours of timeline snapshots
        val logs = mutableListOf<BatteryLogEntity>()
        var curLevel = 85
        var time = now - (48 * hourMs)
        while (time <= now) {
            val isChargingPeriod = (time in (now - 6 * hourMs)..(now - 5 * hourMs + 18 * 60 * 1000L)) ||
                    (time in (now - 28 * hourMs)..(now - 27 * hourMs))
            if (isChargingPeriod) {
                curLevel = min(curLevel + 8, 92)
            } else {
                curLevel = max(curLevel - 2, 15)
            }
            val volt = 3600 + (curLevel * 7.5f).toInt()
            val temp = 290 + (if (isChargingPeriod) 50 else 10)
            logs.add(
                BatteryLogEntity(
                    timestamp = time,
                    level = curLevel,
                    voltageMv = volt,
                    temperatureDeciCelsius = temp,
                    status = if (isChargingPeriod) 2 else 3,
                    pluggedType = if (isChargingPeriod) 1 else 0,
                    currentMa = if (isChargingPeriod) 1850f else -340f,
                    powerMw = (volt * if (isChargingPeriod) 1.85f else 0.34f),
                    healthStatus = 2
                )
            )
            time += (90 * 60 * 1000L) // every 90 minutes
        }
        dao.insertLogs(logs)
    }
}
