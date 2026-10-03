package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BatteryEntry
import com.example.data.model.BatteryLogEntity
import com.example.data.model.BatterySettingEntity
import com.example.data.model.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    // --- Battery Entries (Historical Logs) ---
    @Query("SELECT * FROM battery_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 200): Flow<List<BatteryEntry>>

    @Query("SELECT * FROM battery_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_entries WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getEntriesSince(sinceTimestamp: Long): Flow<List<BatteryEntry>>

    @Query("SELECT * FROM battery_entries WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLogsSince(sinceTimestamp: Long): Flow<List<BatteryLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryEntry(entry: BatteryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BatteryLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryEntries(entries: List<BatteryEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<BatteryLogEntity>)

    @Query("DELETE FROM battery_entries WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldEntries(cutoffTimestamp: Long)

    @Query("DELETE FROM battery_entries WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM battery_entries")
    suspend fun getEntryCount(): Int

    @Query("SELECT COUNT(*) FROM battery_entries")
    suspend fun getLogCount(): Int

    // --- Charge Sessions ---
    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC")
    fun getAllChargeSessions(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions WHERE isCompleted = 1 ORDER BY endTime DESC LIMIT 1")
    fun getLastCompletedSessionFlow(): Flow<ChargeSessionEntity?>

    @Query("SELECT * FROM charge_sessions WHERE isCompleted = 1 ORDER BY endTime DESC LIMIT 1")
    suspend fun getLastCompletedSession(): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveChargeSession(): ChargeSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargeSession(session: ChargeSessionEntity): Long

    @Update
    suspend fun updateChargeSession(session: ChargeSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargeSessions(sessions: List<ChargeSessionEntity>)

    @Query("DELETE FROM charge_sessions WHERE id = :sessionId")
    suspend fun deleteChargeSession(sessionId: Long)

    // --- Settings ---
    @Query("SELECT * FROM battery_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<BatterySettingEntity?>

    @Query("SELECT * FROM battery_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): BatterySettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: BatterySettingEntity)
}
