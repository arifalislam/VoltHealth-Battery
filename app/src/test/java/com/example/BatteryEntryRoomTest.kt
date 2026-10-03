package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BatteryDao
import com.example.data.local.BatteryDatabase
import com.example.data.model.BatteryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryEntryRoomTest {

    private lateinit var db: BatteryDatabase
    private lateinit var batteryDao: BatteryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BatteryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        batteryDao = db.batteryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insert and retrieve BatteryEntry with voltage current and timestamp`() = runBlocking {
        val currentTime = System.currentTimeMillis()
        val entry = BatteryEntry(
            timestamp = currentTime,
            voltageMv = 3980,
            currentMa = -420.5f,
            level = 72,
            scale = 100,
            temperatureDeciCelsius = 312,
            status = 3, // Discharging
            pluggedType = 0,
            powerMw = 3980f * 0.4205f,
            healthStatus = 2
        )

        val id = batteryDao.insertBatteryEntry(entry)
        assertTrue(id > 0)

        val entries = batteryDao.getRecentEntries(10).first()
        assertEquals(1, entries.size)

        val retrieved = entries.first()
        assertEquals(id, retrieved.id)
        assertEquals(currentTime, retrieved.timestamp)
        assertEquals(3980, retrieved.voltageMv)
        assertEquals(-420.5f, retrieved.currentMa, 0.01f)
        assertEquals(72, retrieved.level)
    }

    @Test
    fun `pruneOldEntries deletes entries older than cutoff`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldEntry = BatteryEntry(
            timestamp = now - 100_000,
            voltageMv = 3800,
            currentMa = -350f,
            level = 50
        )
        val newEntry = BatteryEntry(
            timestamp = now,
            voltageMv = 4100,
            currentMa = 1200f,
            level = 80
        )

        batteryDao.insertBatteryEntries(listOf(oldEntry, newEntry))
        assertEquals(2, batteryDao.getEntryCount())

        // Prune entries older than now - 50_000
        batteryDao.pruneOldEntries(now - 50_000)

        val remaining = batteryDao.getRecentEntries().first()
        assertEquals(1, remaining.size)
        assertEquals(4100, remaining.first().voltageMv)
    }
}
