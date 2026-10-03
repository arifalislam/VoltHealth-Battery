package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.model.LiveBatteryState
import com.example.util.PowerCalculatorUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.max

object BatteryMonitor {

    /**
     * Reads factory design capacity via Android PowerProfile internal reflection.
     * Returns null if reflection is blocked or unavailable.
     */
    fun detectDeviceDesignCapacity(context: Context): Int? {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val method = powerProfileClass.getMethod("getBatteryCapacity")
            val capacity = method.invoke(powerProfile) as? Double
            if (capacity != null && capacity > 500.0 && capacity < 20000.0) {
                capacity.toInt()
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Observe live battery changes reactively.
     */
    fun observeLiveBattery(context: Context): Flow<LiveBatteryState> = callbackFlow {
        val appContext = context.applicationContext
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val state = extractLiveState(intent, batteryManager)
                trySend(state)
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = appContext.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            trySend(extractLiveState(initialIntent, batteryManager))
        }

        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    /**
     * Snapshot current battery state synchronously.
     */
    fun getSnapshot(context: Context): LiveBatteryState {
        val appContext = context.applicationContext
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (intent != null) {
            extractLiveState(intent, batteryManager)
        } else {
            LiveBatteryState()
        }
    }

    private fun extractLiveState(intent: Intent, batteryManager: BatteryManager): LiveBatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val rawStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val rawHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850)
        val tempDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 290)
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val percentage = if (scale > 0 && level >= 0) {
            (level.toFloat() / scale.toFloat()) * 100f
        } else 50f

        val isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0
        val isFull = rawStatus == BatteryManager.BATTERY_STATUS_FULL || percentage >= 99.5f

        val statusText = when {
            isFull -> "Full"
            rawStatus == BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            rawStatus == BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            rawStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> if (plugged != 0) "Plugged In" else "Discharging"
        }

        val healthText = when (rawHealth) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Good"
        }

        val pluggedSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "Battery Only"
        }

        // Calculate instantaneous power draw using BatteryManager constants and PowerCalculatorUtil
        val powerTelemetry = PowerCalculatorUtil.calculateInstantaneousPower(batteryManager, intent)
        val currentMa = powerTelemetry.currentMa
        val powerWatts = powerTelemetry.powerWatts

        val rawAvgCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        var avgCurrentMa = PowerCalculatorUtil.normalizeCurrentToMilliAmperes(rawAvgCurrent)
        if (avgCurrentMa == 0f || abs(avgCurrentMa) < 10f) {
            avgCurrentMa = if (isCharging) 1500f else -340f
        }
        if (isCharging && avgCurrentMa < 0) {
            avgCurrentMa = abs(avgCurrentMa)
        } else if (!isCharging && avgCurrentMa > 0) {
            avgCurrentMa = -abs(avgCurrentMa)
        }

        val voltageV = powerTelemetry.voltageVolts
        val tempCelsius = tempDeciC / 10f

        // Estimated remaining hours
        // For discharge: runtime until 0% based on current mA draw and typical 4500mAh capacity
        // For charge: time until 100%
        val estimatedRemainingHours: Float? = if (isCharging) {
            if (percentage >= 99f) 0f
            else {
                val remainingPercent = (100f - percentage) / 100f
                val remainingMah = remainingPercent * 4500f
                val effectiveChargeRate = max(currentMa, 500f)
                remainingMah / effectiveChargeRate
            }
        } else {
            val remainingPercent = percentage / 100f
            val remainingMah = remainingPercent * 4500f
            val effectiveDrainRate = max(abs(currentMa), 150f)
            remainingMah / effectiveDrainRate
        }

        // Discharge speed % per hour
        val dischargeRatePercentPerHour = if (isCharging) {
            (max(currentMa, 500f) / 4500f) * 100f // Charging rate %/hr
        } else {
            (max(abs(currentMa), 150f) / 4500f) * 100f // Drain rate %/hr
        }

        return LiveBatteryState(
            level = max(level, 0),
            scale = scale,
            percentage = percentage,
            isCharging = isCharging,
            isFull = isFull,
            statusText = statusText,
            healthStatusText = healthText,
            pluggedSource = pluggedSource,
            voltageVolts = voltageV,
            voltageMv = voltageMv,
            temperatureCelsius = tempCelsius,
            currentMa = currentMa,
            averageCurrentMa = avgCurrentMa,
            powerWatts = (powerWatts * 100f).toInt() / 100f,
            estimatedRemainingHours = estimatedRemainingHours,
            dischargeRatePercentPerHour = (dischargeRatePercentPerHour * 10f).toInt() / 10f,
            technology = technology
        )
    }
}
