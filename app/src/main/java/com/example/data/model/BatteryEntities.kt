package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing historical logs of battery voltage, current flow,
 * timestamps, and operational metrics for battery health tracking.
 */
@Entity(tableName = "battery_entries")
data class BatteryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val voltageMv: Int,
    val currentMa: Float, // Negative for discharging, positive for charging
    val level: Int = 50,
    val scale: Int = 100,
    val temperatureDeciCelsius: Int = 290, // e.g. 325 = 32.5 °C
    val status: Int = 3, // BatteryManager.BATTERY_STATUS_*
    val pluggedType: Int = 0, // BatteryManager.BATTERY_PLUGGED_* (0 = unplugged)
    val powerMw: Float = 0f, // Power in mW (Voltage * Current)
    val healthStatus: Int = 2 // BatteryManager.BATTERY_HEALTH_*
)

typealias BatteryLogEntity = BatteryEntry

/**
 * Record of a charge session from plugged-in to unplugged.
 * Used for estimating total battery capacity via Coulomb integration / delta percentage.
 */
@Entity(tableName = "charge_sessions")
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startLevel: Int,
    val endLevel: Int,
    val startVoltageMv: Int = 0,
    val endVoltageMv: Int = 0,
    val pluggedType: Int = 1, // AC = 1, USB = 2, Wireless = 4
    val durationMs: Long = 0,
    val energyAddedMah: Float = 0f, // Energy transferred in mAh
    val estimatedFullCapacityMah: Float? = null, // Derived total capacity if delta >= 10%
    val isCompleted: Boolean = false
)

/**
 * Persistent device battery configuration and calibration settings.
 */
@Entity(tableName = "battery_settings")
data class BatterySettingEntity(
    @PrimaryKey
    val id: Int = 1,
    val designCapacityMah: Int = 5000,
    val userCustomCapacity: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val highTempAlertThresholdC: Float = 42.0f,
    val chargeLimitAlertPercent: Int = 80
)

/**
 * Real-time composite state observed from BatteryManager and database analytics.
 */
data class LiveBatteryState(
    val level: Int = 50,
    val scale: Int = 100,
    val percentage: Float = 50f,
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    val statusText: String = "Discharging",
    val healthStatusText: String = "Good",
    val pluggedSource: String = "Battery",
    val voltageVolts: Float = 3.85f,
    val voltageMv: Int = 3850,
    val temperatureCelsius: Float = 30.5f,
    val currentMa: Float = -350f, // Instantaneous current (mA)
    val averageCurrentMa: Float = -320f,
    val powerWatts: Float = 1.35f, // Instantaneous power (W)
    val estimatedRemainingHours: Float? = 5.2f, // Hours remaining until empty or full
    val dischargeRatePercentPerHour: Float = 12.5f, // %/hr
    val technology: String = "Li-ion"
)

/**
 * Battery Health analysis and capacity estimation result.
 */
data class BatteryHealthReport(
    val designCapacityMah: Int = 5000,
    val estimatedTotalCapacityMah: Float = 4650f,
    val healthPercentage: Float = 93.0f,
    val wearPercentage: Float = 7.0f,
    val confidence: String = "High",
    val sessionsAnalyzedCount: Int = 0,
    val estimatedCycleCount: Float = 142.5f,
    val healthRating: HealthRating = HealthRating.GOOD,
    val averageChargeRateMahPerMin: Float = 45f,
    val lastChargedTimestamp: Long? = null,
    val lastChargedDurationMs: Long = 0,
    val lastChargedLevelGained: Int = 0,
    val lastChargedStartLevel: Int = 0,
    val lastChargedEndLevel: Int = 0
)

enum class HealthRating(val label: String, val description: String) {
    EXCELLENT("Excellent", "Battery retains 90%+ capacity with minimal wear."),
    GOOD("Good", "Battery health is standard, performing within normal specs."),
    DEGRADED("Degraded", "Capacity has decreased below 80%. Runtime may be reduced."),
    CRITICAL("Service Recommended", "Battery has lost significant capacity. Consider replacement.")
}
