package com.qtekfun.ultimatealarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qtekfun.ultimatealarm.core.designsystem.UltimateAlarmTheme
import com.qtekfun.ultimatealarm.diagnostics.DiagnosticsScreen
import com.qtekfun.ultimatealarm.feature.alarms.AlarmEditorScreen
import com.qtekfun.ultimatealarm.feature.alarms.AlarmEditorViewModel
import com.qtekfun.ultimatealarm.feature.alarms.AlarmListScreen
import com.qtekfun.ultimatealarm.feature.alarms.ExceptionsScreen
import com.qtekfun.ultimatealarm.feature.alarms.VacationsScreen
import com.qtekfun.ultimatealarm.feature.backup.BackupScreen
import com.qtekfun.ultimatealarm.feature.clock.StopwatchScreen
import com.qtekfun.ultimatealarm.feature.clock.TimerScreen
import com.qtekfun.ultimatealarm.settings.HolidaySettingsScreen
import com.qtekfun.ultimatealarm.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UltimateAlarmTheme {
                AppNavHost()
            }
        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val EDITOR = "editor/{${AlarmEditorViewModel.ARG_ALARM_ID}}"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val HOLIDAYS = "settings/holidays"
    const val VACATIONS = "vacations"
    const val BACKUP = "settings/backup"
    const val EXCEPTIONS = "exceptions/{${AlarmEditorViewModel.ARG_ALARM_ID}}"

    fun exceptions(id: Long) = "exceptions/$id"

    fun editor(id: Long) = "editor/$id"
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onEdit = { nav.navigate(Routes.editor(it)) },
                onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onVacations = { nav.navigate(Routes.VACATIONS) }
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument(AlarmEditorViewModel.ARG_ALARM_ID) { type = NavType.LongType })) {
            AlarmEditorScreen(onDone = { _, _ -> nav.popBackStack() }, onBack = { nav.popBackStack() }, onExceptions = { nav.navigate(Routes.exceptions(it)) })
        }
        composable(Routes.SETTINGS) {
            BackScaffold(stringResource(R.string.settings), onBack = {
                nav.popBackStack()
            }) { SettingsScreen(onHolidays = { nav.navigate(Routes.HOLIDAYS) }, onBackup = { nav.navigate(Routes.BACKUP) }, modifier = it) }
        }
        composable(Routes.HOLIDAYS) {
            BackScaffold(stringResource(R.string.settings_holidays), onBack = { nav.popBackStack() }) { HolidaySettingsScreen(it) }
        }
        composable(Routes.EXCEPTIONS, arguments = listOf(navArgument(AlarmEditorViewModel.ARG_ALARM_ID) { type = NavType.LongType })) {
            BackScaffold(stringResource(com.qtekfun.ultimatealarm.feature.alarms.R.string.exceptions_open), onBack = {
                nav.popBackStack()
            }) { ExceptionsScreen(it) }
        }
        composable(Routes.BACKUP) {
            BackScaffold(stringResource(R.string.settings_backup), onBack = { nav.popBackStack() }) { BackupScreen(it) }
        }
        composable(Routes.VACATIONS) {
            BackScaffold(stringResource(com.qtekfun.ultimatealarm.feature.alarms.R.string.vacations), onBack = { nav.popBackStack() }) { VacationsScreen(it) }
        }
        composable(Routes.DIAGNOSTICS) {
            BackScaffold(stringResource(R.string.diagnostics), onBack = { nav.popBackStack() }) { DiagnosticsScreen(it) }
        }
    }
}

/** Top-level destinations of the bottom navigation (§10; holidays live in Settings, D-14). */
private enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    ALARMS(R.string.nav_alarms, Icons.Outlined.Alarm),
    TIMER(R.string.nav_timer, Icons.Outlined.HourglassEmpty),
    STOPWATCH(R.string.nav_stopwatch, Icons.Outlined.Timer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onEdit: (Long) -> Unit, onDiagnostics: () -> Unit, onSettings: () -> Unit, onVacations: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.ALARMS) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(current.label)) },
                actions = {
                    if (current == Destination.ALARMS) {
                        IconButton(onClick = onVacations) {
                            Icon(Icons.Outlined.BeachAccess, contentDescription = stringResource(com.qtekfun.ultimatealarm.feature.alarms.R.string.vacations))
                        }
                    }
                    IconButton(onClick = onDiagnostics) { Icon(Icons.Outlined.MonitorHeart, contentDescription = stringResource(R.string.diagnostics)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings)) }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { current = destination },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PermissionBanner(onFix = onDiagnostics)
            when (current) {
                Destination.ALARMS -> AlarmListScreen(onEdit = onEdit)
                Destination.TIMER -> TimerScreen()
                Destination.STOPWATCH -> StopwatchScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding -> content(Modifier.padding(padding)) }
}
