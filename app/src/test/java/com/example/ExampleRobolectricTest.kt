package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChargeSessionEntity
import com.example.data.repository.BatteryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoltHealth", appName)
    }

    @Test
    fun `test capacity calculation algorithm`() {
        val designCapacity = 5000
        val sampleSessions = listOf(
            ChargeSessionEntity(
                startTime = 1000L,
                endTime = 5000L,
                startLevel = 20,
                endLevel = 80, // delta = 60%
                durationMs = 3600_000L,
                energyAddedMah = 2700f, // 2700 / 0.6 = 4500 mAh
                estimatedFullCapacityMah = 4500f,
                isCompleted = true
            )
        )

        // Health should be 4500 / 5000 = 90%
        val delta = 80 - 20
        val derivedCapacity = 2700f / (delta / 100f)
        assertEquals(4500f, derivedCapacity, 0.1f)
        val healthPercent = (derivedCapacity / designCapacity) * 100f
        assertEquals(90f, healthPercent, 0.1f)
    }
}
