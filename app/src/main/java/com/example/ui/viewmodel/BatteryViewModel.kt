package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BatteryDatabase
import com.example.data.model.BatteryHealthReport
import com.example.data.model.BatteryLogEntity
import com.example.data.model.BatterySettingEntity
import com.example.data.model.ChargeSessionEntity
import com.example.data.model.LiveBatteryState
import com.example.data.receiver.BatteryMonitor
import com.example.data.repository.BatteryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BatteryDatabase.getDatabase(application)
    private val repository = BatteryRepository(database.batteryDao(), application)

    // Live state observed from BatteryManager
    private val _liveState = MutableStateFlow(BatteryMonitor.getSnapshot(application))
    val liveState: StateFlow<LiveBatteryState> = _liveState.asStateFlow()

    // Database streams
    val settings: StateFlow<BatterySettingEntity> = repository.settingsFlow
        .mapState(BatterySettingEntity(designCapacityMah = 5000))

    val recentLogs: StateFlow<List<BatteryLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chargeSessions: StateFlow<List<ChargeSessionEntity>> = repository.chargeSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastCompletedSession: StateFlow<ChargeSessionEntity?> = repository.lastCompletedSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Combined Health & Capacity Report
    val healthReport: StateFlow<BatteryHealthReport> = combine(
        settings,
        chargeSessions
    ) { currentSettings, sessions ->
        repository.getHealthReport(currentSettings.designCapacityMah, sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryHealthReport())

    // Selected time filter for history chart ("24h", "48h", "7d")
    private val _historyTimeRange = MutableStateFlow("48h")
    val historyTimeRange: StateFlow<String> = _historyTimeRange.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeSettingsIfNeeded()
        }

        // Listen to live broadcast updates
        viewModelScope.launch {
            BatteryMonitor.observeLiveBattery(application).collect { state ->
                _liveState.value = state
                repository.recordBatterySnapshot(state)
            }
        }

        // Periodic ticker to record snapshots and refresh active power metrics
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L) // every 30 seconds
                val snapshot = BatteryMonitor.getSnapshot(application)
                _liveState.value = snapshot
                repository.recordBatterySnapshot(snapshot)
            }
        }
    }

    fun setHistoryTimeRange(range: String) {
        _historyTimeRange.value = range
    }

    fun updateDesignCapacity(capacityMah: Int) {
        viewModelScope.launch {
            repository.updateDesignCapacity(capacityMah)
        }
    }

    fun forceRefresh() {
        val snapshot = BatteryMonitor.getSnapshot(getApplication())
        _liveState.value = snapshot
        viewModelScope.launch {
            repository.recordBatterySnapshot(snapshot)
        }
    }

    fun logManualChargeSession(startLevel: Int, endLevel: Int, durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationMs = durationMinutes * 60 * 1000L
            val designCap = settings.value.designCapacityMah
            val delta = endLevel - startLevel
            if (delta > 0) {
                val energyAdded = (delta / 100f) * (designCap * 0.92f)
                val estCapacity = energyAdded / (delta / 100f)
                val session = ChargeSessionEntity(
                    startTime = now - durationMs,
                    endTime = now,
                    startLevel = startLevel,
                    endLevel = endLevel,
                    startVoltageMv = 3700,
                    endVoltageMv = 4250,
                    durationMs = durationMs,
                    energyAddedMah = energyAdded,
                    estimatedFullCapacityMah = estCapacity,
                    isCompleted = true
                )
                database.batteryDao().insertChargeSession(session)
            }
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T?>.mapState(default: T): StateFlow<T> =
        combine(kotlinx.coroutines.flow.flowOf(Unit)) { it, _ -> it }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                default
            )
            .let { baseFlow ->
                val flow = MutableStateFlow(default)
                viewModelScope.launch {
                    this@mapState.collect { value ->
                        flow.value = value ?: default
                    }
                }
                flow.asStateFlow()
            }
}
