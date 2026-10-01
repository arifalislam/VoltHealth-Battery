package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BatteryLogEntity
import com.example.data.model.BatterySettingEntity
import com.example.data.model.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    // --- Battery Logs ---
    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLogsSince(sinceTimestamp: Long): Flow<List<BatteryLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BatteryLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<BatteryLogEntity>)

    @Query("DELETE FROM battery_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM battery_logs")
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
