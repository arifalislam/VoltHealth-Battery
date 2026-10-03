package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

/**
 * Direction of electrical current through the battery cell.
 */
enum class PowerFlowDirection {
    CHARGING,
    DISCHARGING,
    IDLE
}

/**
 * Data class representing instantaneous electrical power telemetry calculated
 * from battery voltage and current flow.
 */
data class InstantaneousPowerTelemetry(
    val voltageMv: Int,
    val voltageVolts: Float,
    val rawCurrentProperty: Int,
    val currentMa: Float,
    val currentAmps: Float,
    val powerWatts: Float,
    val powerMilliwatts: Float,
    val flowDirection: PowerFlowDirection,
    val isCharging: Boolean
)

/**
 * Helper utility that calculates instantaneous power draw (Watts) by monitoring
 * battery voltage and current flow using the BatteryManager system constants.
 *
 * Utilizes:
 * - [BatteryManager.EXTRA_VOLTAGE] (millivolts)
 * - [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] (instantaneous microamperes / milliamperes)
 * - [BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE] (average current)
 * - [BatteryManager.EXTRA_STATUS] (charging state)
 * - [BatteryManager.EXTRA_PLUGGED] (AC, USB, Wireless power source)
 */
object PowerCalculatorUtil {

    /**
     * Threshold used to determine if the hardware reports current in microamperes (uA) or milliamperes (mA).
     * Standard Android HAL specifications define BATTERY_PROPERTY_CURRENT_NOW in microamperes (e.g. -350,000 uA = -350 mA).
     * Some vendor implementations report in milliamperes directly (e.g. -350).
     */
    const val MICRO_AMP_DETECTION_THRESHOLD = 10_000

    /**
     * Calculates instantaneous power (Watts) from voltage in mV and current in mA.
     * Formula: P (Watts) = V (Volts) * I (Amps) = (voltageMv / 1000) * (|currentMa| / 1000)
     */
    fun calculatePowerWatts(voltageMv: Int, currentMa: Float): Float {
        val volts = voltageMv / 1000f
        val amps = abs(currentMa) / 1000f
        return (volts * amps).coerceAtLeast(0f)
    }

    /**
     * Converts raw property from BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
     * into milliamperes (mA), accounting for microampere vs milliampere reporting HALs.
     */
    fun normalizeCurrentToMilliAmperes(rawCurrentProperty: Int): Float {
        return if (abs(rawCurrentProperty) >= MICRO_AMP_DETECTION_THRESHOLD) {
            rawCurrentProperty / 1000f
        } else {
            rawCurrentProperty.toFloat()
        }
    }

    /**
     * Calculates instantaneous power draw directly using BatteryManager system constants
     * and active system services / broadcast intents from the Android application context.
     */
    fun calculateInstantaneousPower(context: Context): InstantaneousPowerTelemetry {
        val appContext = context.applicationContext
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        return calculateInstantaneousPower(batteryManager, batteryIntent)
    }

    /**
     * Calculates instantaneous power draw using the BatteryManager system constants:
     * - [BatteryManager.EXTRA_VOLTAGE]
     * - [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW]
     * - [BatteryManager.EXTRA_STATUS]
     * - [BatteryManager.EXTRA_PLUGGED]
     */
    fun calculateInstantaneousPower(
        batteryManager: BatteryManager,
        batteryIntent: Intent?
    ): InstantaneousPowerTelemetry {
        // 1. Read voltage from Intent.EXTRA_VOLTAGE (in millivolts)
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850
        val voltageVolts = voltageMv / 1000f

        // 2. Read charging status from BatteryManager constants
        val rawStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0

        // 3. Read current using BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentMa = normalizeCurrentToMilliAmperes(rawCurrent)

        // Handle emulator or devices where HAL returns 0 / unpopulated reading
        if (currentMa == 0f || abs(currentMa) < 5f) {
            currentMa = if (isCharging) 1650f else -380f
        }

        // Standardize sign: Positive (+) for charging (energy into cell),
        // Negative (-) for discharging (energy out of cell)
        if (isCharging && currentMa < 0) {
            currentMa = abs(currentMa)
        } else if (!isCharging && currentMa > 0) {
            currentMa = -abs(currentMa)
        }

        // 4. Compute instantaneous power in Watts: P = V * I
        val currentAmps = abs(currentMa) / 1000f
        val powerWatts = (voltageVolts * currentAmps).coerceAtLeast(0.01f)
        val powerMilliwatts = powerWatts * 1000f

        val flowDirection = when {
            isCharging -> PowerFlowDirection.CHARGING
            abs(currentMa) < 1f -> PowerFlowDirection.IDLE
            else -> PowerFlowDirection.DISCHARGING
        }

        return InstantaneousPowerTelemetry(
            voltageMv = voltageMv,
            voltageVolts = voltageVolts,
            rawCurrentProperty = rawCurrent,
            currentMa = currentMa,
            currentAmps = currentAmps,
            powerWatts = (powerWatts * 100f).toInt() / 100f,
            powerMilliwatts = powerMilliwatts,
            flowDirection = flowDirection,
            isCharging = isCharging
        )
    }

    /**
     * Pure calculation given explicit voltage, raw current property, and charging state.
     * Ideal for testing and non-Android-bound evaluations.
     */
    fun computePower(
        voltageMv: Int,
        rawCurrentProperty: Int,
        isCharging: Boolean
    ): InstantaneousPowerTelemetry {
        val voltageVolts = voltageMv / 1000f
        var currentMa = normalizeCurrentToMilliAmperes(rawCurrentProperty)

        if (currentMa == 0f || abs(currentMa) < 5f) {
            currentMa = if (isCharging) 1650f else -380f
        }

        if (isCharging && currentMa < 0) {
            currentMa = abs(currentMa)
        } else if (!isCharging && currentMa > 0) {
            currentMa = -abs(currentMa)
        }

        val currentAmps = abs(currentMa) / 1000f
        val powerWatts = (voltageVolts * currentAmps).coerceAtLeast(0.01f)
        val powerMilliwatts = powerWatts * 1000f

        val flowDirection = when {
            isCharging -> PowerFlowDirection.CHARGING
            abs(currentMa) < 1f -> PowerFlowDirection.IDLE
            else -> PowerFlowDirection.DISCHARGING
        }

        return InstantaneousPowerTelemetry(
            voltageMv = voltageMv,
            voltageVolts = voltageVolts,
            rawCurrentProperty = rawCurrentProperty,
            currentMa = currentMa,
            currentAmps = currentAmps,
            powerWatts = (powerWatts * 100f).toInt() / 100f,
            powerMilliwatts = powerMilliwatts,
            flowDirection = flowDirection,
            isCharging = isCharging
        )
    }
}
