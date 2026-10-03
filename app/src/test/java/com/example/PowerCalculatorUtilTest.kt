package com.example

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.util.PowerCalculatorUtil
import com.example.util.PowerFlowDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerCalculatorUtilTest {

    @Test
    fun `calculatePowerWatts computes correct wattage from mV and mA`() {
        // 4000 mV = 4.0 V, 500 mA = 0.5 A -> 4.0 * 0.5 = 2.0 W
        val watts = PowerCalculatorUtil.calculatePowerWatts(4000, 500f)
        assertEquals(2.0f, watts, 0.001f)

        // Discharging current (negative mA) should still produce positive absolute power (Watts)
        val dischargeWatts = PowerCalculatorUtil.calculatePowerWatts(3800, -350f)
        // 3.8 * 0.35 = 1.33 W
        assertEquals(1.33f, dischargeWatts, 0.001f)
    }

    @Test
    fun `normalizeCurrentToMilliAmperes converts microamperes and keeps milliamperes`() {
        // Standard HAL microamperes (> 10,000 threshold)
        val microAmps = -450_000
        val normalizedFromMicro = PowerCalculatorUtil.normalizeCurrentToMilliAmperes(microAmps)
        assertEquals(-450.0f, normalizedFromMicro, 0.01f)

        // Vendor HAL already reporting in milliamperes
        val milliAmps = -350
        val normalizedFromMilli = PowerCalculatorUtil.normalizeCurrentToMilliAmperes(milliAmps)
        assertEquals(-350.0f, normalizedFromMilli, 0.01f)
    }

    @Test
    fun `computePower detects discharging direction and calculates metrics`() {
        val voltageMv = 3850 // 3.85 V
        val rawCurrent = -400_000 // -400 mA
        val isCharging = false

        val telemetry = PowerCalculatorUtil.computePower(
            voltageMv = voltageMv,
            rawCurrentProperty = rawCurrent,
            isCharging = isCharging
        )

        assertEquals(3850, telemetry.voltageMv)
        assertEquals(3.85f, telemetry.voltageVolts, 0.001f)
        assertEquals(-400f, telemetry.currentMa, 0.01f)
        assertEquals(0.4f, telemetry.currentAmps, 0.01f)
        // P = 3.85 * 0.4 = 1.54 W
        assertEquals(1.54f, telemetry.powerWatts, 0.01f)
        assertEquals(PowerFlowDirection.DISCHARGING, telemetry.flowDirection)
        assertFalse(telemetry.isCharging)
    }

    @Test
    fun `computePower detects charging direction and calculates metrics`() {
        val voltageMv = 4200 // 4.2 V
        val rawCurrent = 2_000_000 // 2000 mA
        val isCharging = true

        val telemetry = PowerCalculatorUtil.computePower(
            voltageMv = voltageMv,
            rawCurrentProperty = rawCurrent,
            isCharging = isCharging
        )

        assertEquals(4200, telemetry.voltageMv)
        assertEquals(4.20f, telemetry.voltageVolts, 0.001f)
        assertEquals(2000f, telemetry.currentMa, 0.01f)
        assertEquals(2.0f, telemetry.currentAmps, 0.01f)
        // P = 4.2 * 2.0 = 8.4 W
        assertEquals(8.40f, telemetry.powerWatts, 0.01f)
        assertEquals(PowerFlowDirection.CHARGING, telemetry.flowDirection)
        assertTrue(telemetry.isCharging)
    }

    @Test
    fun `calculateInstantaneousPower reads BatteryManager system constants from intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }

        val telemetry = PowerCalculatorUtil.calculateInstantaneousPower(batteryManager, intent)

        assertEquals(4100, telemetry.voltageMv)
        assertEquals(4.1f, telemetry.voltageVolts, 0.001f)
        assertTrue(telemetry.isCharging)
        assertTrue(telemetry.powerWatts > 0f)
    }
}
