package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HealthCapacityScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PowerCalculatorScreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.VoltHealthTheme
import com.example.ui.viewmodel.BatteryViewModel

enum class NavScreen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DASHBOARD("Power", Icons.Filled.ElectricBolt, Icons.Outlined.ElectricBolt, "tab_dashboard"),
    HEALTH("Health", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "tab_health"),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History, "tab_history"),
    CALCULATOR("Calculator", Icons.Filled.Calculate, Icons.Outlined.Calculate, "tab_calculator")
}

class MainActivity : ComponentActivity() {

    private val viewModel: BatteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VoltHealthTheme {
                val liveState by viewModel.liveState.collectAsStateWithLifecycle()
                val healthReport by viewModel.healthReport.collectAsStateWithLifecycle()
                val sessions by viewModel.chargeSessions.collectAsStateWithLifecycle()
                val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
                val lastSession by viewModel.lastCompletedSession.collectAsStateWithLifecycle()
                val historyTimeRange by viewModel.historyTimeRange.collectAsStateWithLifecycle()

                var currentScreen by rememberSaveable { mutableStateOf(NavScreen.DASHBOARD) }

                // System Back button returns to Dashboard if on another screen
                BackHandler(enabled = currentScreen != NavScreen.DASHBOARD) {
                    currentScreen = NavScreen.DASHBOARD
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 600.dp

                    if (isWideScreen) {
                        // Wide Screen: Navigation Rail Layout
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .testTag("app_navigation_rail")
                            ) {
                                NavScreen.values().forEach { screen ->
                                    val isSelected = currentScreen == screen
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = { currentScreen = screen },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = { Text(screen.title) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = EmeraldLight,
                                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.testTag(screen.tag)
                                    )
                                }
                            }

                            Scaffold(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("main_scaffold")
                            ) { innerPadding ->
                                ScreenContent(
                                    currentScreen = currentScreen,
                                    viewModel = viewModel,
                                    liveState = liveState,
                                    healthReport = healthReport,
                                    sessions = sessions,
                                    logs = logs,
                                    lastSession = lastSession,
                                    historyTimeRange = historyTimeRange,
                                    onNavigateToHealth = { currentScreen = NavScreen.HEALTH },
                                    onNavigateToCalculator = { currentScreen = NavScreen.CALCULATOR },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    } else {
                        // Handheld Vertical Screen: Bottom NavigationBar Layout
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    modifier = Modifier.testTag("app_bottom_bar")
                                ) {
                                    NavScreen.values().forEach { screen ->
                                        val isSelected = currentScreen == screen
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentScreen = screen },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = screen.title
                                                )
                                            },
                                            label = { Text(screen.title) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = EmeraldLight,
                                                indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                                            ),
                                            modifier = Modifier.testTag(screen.tag)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("main_scaffold")
                        ) { innerPadding ->
                            ScreenContent(
                                currentScreen = currentScreen,
                                viewModel = viewModel,
                                liveState = liveState,
                                healthReport = healthReport,
                                sessions = sessions,
                                logs = logs,
                                lastSession = lastSession,
                                historyTimeRange = historyTimeRange,
                                onNavigateToHealth = { currentScreen = NavScreen.HEALTH },
                                onNavigateToCalculator = { currentScreen = NavScreen.CALCULATOR },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    currentScreen: NavScreen,
    viewModel: BatteryViewModel,
    liveState: com.example.data.model.LiveBatteryState,
    healthReport: com.example.data.model.BatteryHealthReport,
    sessions: List<com.example.data.model.ChargeSessionEntity>,
    logs: List<com.example.data.model.BatteryLogEntity>,
    lastSession: com.example.data.model.ChargeSessionEntity?,
    historyTimeRange: String,
    onNavigateToHealth: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentScreen) {
        NavScreen.DASHBOARD -> DashboardScreen(
            liveState = liveState,
            healthReport = healthReport,
            lastSession = lastSession,
            onNavigateToHealth = onNavigateToHealth,
            onNavigateToCalculator = onNavigateToCalculator,
            onRefresh = { viewModel.forceRefresh() },
            modifier = modifier
        )
        NavScreen.HEALTH -> HealthCapacityScreen(
            healthReport = healthReport,
            sessions = sessions,
            onUpdateDesignCapacity = { newCap -> viewModel.updateDesignCapacity(newCap) },
            modifier = modifier
        )
        NavScreen.HISTORY -> HistoryScreen(
            logs = logs,
            sessions = sessions,
            selectedTimeRange = historyTimeRange,
            onSelectTimeRange = { range -> viewModel.setHistoryTimeRange(range) },
            onLogManualSession = { startLvl, endLvl, durationMins ->
                viewModel.logManualChargeSession(startLvl, endLvl, durationMins)
            },
            modifier = modifier
        )
        NavScreen.CALCULATOR -> PowerCalculatorScreen(
            liveState = liveState,
            healthReport = healthReport,
            modifier = modifier
        )
    }
}
