// MainActivity: the app's only screen. An Activity is Android's unit of "a screen the user can open";
// with Jetpack Compose it just hosts composable functions via setContent. It shows onboarding until
// setup is finished once, then the settings screen. It's also what a long-press on the tile opens.
package com.prestoza.inboxonly.ui

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prestoza.inboxonly.R
import com.prestoza.inboxonly.appContainer
import com.prestoza.inboxonly.domain.Settings
import com.prestoza.inboxonly.service.FocusTileService
import com.prestoza.inboxonly.service.PermissionIntents
import com.prestoza.inboxonly.service.PermissionStatus
import com.prestoza.inboxonly.ui.theme.InboxOnlyTheme
import kotlinx.coroutines.launch

/** Hosts onboarding and the settings screen. Daily use happens through the Quick Settings tile, not here. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { InboxOnlyTheme { AppRoot() } }
    }

    @Composable
    private fun AppRoot() {
        val container = appContainer
        val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
        val completed by container.onboardingStore.completed.collectAsStateWithLifecycle(initialValue = null)
        val batteryStepDone by container.onboardingStore.batteryStepDone.collectAsStateWithLifecycle(
            initialValue = false,
        )
        val tileAdded by container.onboardingStore.tileAdded.collectAsStateWithLifecycle(initialValue = false)

        // Permissions change in system settings, outside the app, so re-read them whenever we come back.
        var permissions by remember { mutableStateOf(PermissionStatus.read(this)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = PermissionStatus.read(this@MainActivity) }

        val currentSettings = settings ?: return
        val onboardingCompleted = completed ?: return

        if (!onboardingCompleted) {
            val step = nextOnboardingStep(permissions.accessibilityEnabled, batteryStepDone, tileAdded)
            if (step == null) {
                LaunchedEffect(Unit) { container.onboardingStore.markCompleted() }
                return
            }
            OnboardingScreen(
                step = step,
                isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
                batteryExempt = permissions.batteryOptimizationIgnored,
                canRequestTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                actions = onboardingActions(),
            )
        } else {
            SettingsScreen(
                settings = currentSettings,
                permissions = permissions,
                rulesArePlaceholder = container.rulesRepository.rules.placeholder,
                use24Hour = DateFormat.is24HourFormat(this),
                onUpdate = ::updateSettings,
                onFix = ::fix,
            )
        }
    }

    private fun onboardingActions(): OnboardingActions {
        val container = appContainer
        return OnboardingActions(
            openAccessibilitySettings = { open(PermissionIntents.accessibilitySettings()) },
            openAppInfo = { open(PermissionIntents.appInfo(this)) },
            requestBatteryExemption = { fix(PermissionFix.BATTERY) },
            finishBatteryStep = {
                container.applicationScope.launch { container.onboardingStore.markBatteryStepDone() }
            },
            requestAddTile = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestAddTile() },
            confirmTileAdded = { container.applicationScope.launch { container.onboardingStore.markTileAdded() } },
        )
    }

    // Saved through the coordinator so a schedule change takes effect (and re-arms its alarm) right away.
    // Launched in the application scope so leaving the screen mid-save can't cancel the write.
    private fun updateSettings(transform: (Settings) -> Settings) {
        val container = appContainer
        container.applicationScope.launch { container.focusCoordinator.updateSettings(transform) }
    }

    private fun fix(fix: PermissionFix) {
        when (fix) {
            PermissionFix.ACCESSIBILITY -> {
                open(PermissionIntents.accessibilitySettings())
            }

            PermissionFix.BATTERY -> {
                if (!open(PermissionIntents.batteryOptimizationRequest(this))) {
                    open(PermissionIntents.batteryOptimizationList())
                }
            }

            PermissionFix.EXACT_ALARMS -> {
                PermissionIntents.exactAlarmSettings(this)?.let(::open)
            }
        }
    }

    /** Asks the system to add our tile (Android 13+). The system shows its own confirmation dialog. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        val container = appContainer
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, FocusTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile_focus),
            mainExecutor,
        ) { result ->
            val added =
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            if (added) container.applicationScope.launch { container.onboardingStore.markTileAdded() }
        }
    }

    // Some phones lack some system screens; returns false instead of crashing.
    private fun open(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
