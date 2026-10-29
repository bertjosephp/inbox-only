// OnboardingScreen: the three-step setup shown on first launch, until it's finished once.
// 1) accessibility (with the "restricted settings" workaround for sideloaded apps), 2) battery
// optimization (with Samsung's "Never sleeping apps"), 3) adding the Quick Settings tile.
// Stateless: progress comes from PermissionStatus and OnboardingStore via MainActivity.
package com.prestoza.inboxonly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Which onboarding step to show. */
enum class OnboardingStep { ACCESSIBILITY, BATTERY, TILE }

/** Callbacks for the buttons on each step. */
data class OnboardingActions(
    val openAccessibilitySettings: () -> Unit,
    val openAppInfo: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    val finishBatteryStep: () -> Unit,
    val requestAddTile: () -> Unit,
    val confirmTileAdded: () -> Unit,
)

/**
 * One step of onboarding.
 *
 * @param isSamsung show Samsung-specific battery guidance.
 * @param batteryExempt whether battery optimization is already off for this app.
 * @param canRequestTile true on Android 13+, where the app can ask to add its tile directly.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    isSamsung: Boolean,
    batteryExempt: Boolean,
    canRequestTile: Boolean,
    actions: OnboardingActions,
) {
    Scaffold { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            Text("Setup · step ${step.ordinal + 1} of 3", style = MaterialTheme.typography.labelLarge)
            when (step) {
                OnboardingStep.ACCESSIBILITY -> AccessibilityStep(actions)
                OnboardingStep.BATTERY -> BatteryStep(isSamsung, batteryExempt, actions)
                OnboardingStep.TILE -> TileStep(canRequestTile, actions)
            }
        }
    }
}

@Composable
private fun AccessibilityStep(actions: OnboardingActions) {
    Text("Turn on the blocker", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Inbox Only uses an accessibility service to notice when Reels, a feed, Shorts or Reddit browsing " +
            "opens in Instagram, Facebook, YouTube or Reddit, and to press Back for you. It only looks at which " +
            "screen is showing. It never reads or stores your messages, and it has no internet access.",
    )
    Text(
        "In the list that opens, tap Inbox Only (it may be under \"Installed apps\" or \"Downloaded apps\") " +
            "and switch it on.",
    )
    Button(onClick = actions.openAccessibilitySettings) { Text("Open accessibility settings") }

    Text("Switch greyed out or \"Restricted setting\"?", style = MaterialTheme.typography.titleMedium)
    Text(
        "Android 13 and newer block this for apps installed outside the Play Store until you allow it once:\n" +
            "1. Tap \"Open App info\" below.\n" +
            "2. Tap ⋮ (top right) → \"Allow restricted settings\", and confirm.\n" +
            "3. Come back here and try again.",
    )
    OutlinedButton(onClick = actions.openAppInfo) { Text("Open App info") }
    Text("This step finishes by itself once the service is on.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BatteryStep(
    isSamsung: Boolean,
    batteryExempt: Boolean,
    actions: OnboardingActions,
) {
    Text("Keep it running", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Phones put apps to sleep to save battery, which can stop the schedule from turning Focus on and off " +
            "on time. Inbox Only does no background work of its own, so allowing it costs essentially nothing.",
    )
    if (batteryExempt) {
        Text("✓ Battery optimization is off for Inbox Only.")
    } else {
        Button(onClick = actions.requestBatteryExemption) { Text("Allow") }
    }
    if (isSamsung) {
        Text("On Samsung, also do this:", style = MaterialTheme.typography.titleMedium)
        Text(
            "Settings → Battery → Background usage limits → Never sleeping apps → + → Inbox Only → Add.\n" +
                "(On some One UI versions it's under Settings → Device care → Battery.)",
        )
    }
    TextButton(onClick = actions.finishBatteryStep) { Text(if (batteryExempt) "Next" else "Skip for now") }
}

@Composable
private fun TileStep(
    canRequestTile: Boolean,
    actions: OnboardingActions,
) {
    Text("Add the Focus tile", style = MaterialTheme.typography.headlineSmall)
    Text(
        "You'll use Inbox Only from a Quick Settings tile, not from this app. Tap it to turn Focus on or off; " +
            "long-press it to come back to these settings.",
    )
    if (canRequestTile) {
        Button(onClick = actions.requestAddTile) { Text("Add tile") }
        Text("If that doesn't work, add it by hand:", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "1. Swipe down twice from the top of the screen.\n" +
            "2. Tap the pencil (edit) icon.\n" +
            "3. Find \"Focus\" with the Inbox Only icon and drag it into your tiles.",
    )
    OutlinedButton(onClick = actions.confirmTileAdded) { Text("I've added it") }
}

/** The first unfinished step, or null when setup is complete. Pure, so it's unit tested. */
fun nextOnboardingStep(
    accessibilityEnabled: Boolean,
    batteryStepDone: Boolean,
    tileAdded: Boolean,
): OnboardingStep? =
    when {
        !accessibilityEnabled -> OnboardingStep.ACCESSIBILITY
        !batteryStepDone -> OnboardingStep.BATTERY
        !tileAdded -> OnboardingStep.TILE
        else -> null
    }
