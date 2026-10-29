// FocusTileService: the Quick Settings tile, which is the app's main control.
// A TileService is bound by the system only while the Quick Settings shade is showing the tile
// (onStartListening/onStopListening), so updating the tile there costs nothing the rest of the time.
package com.prestoza.inboxonly.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.widget.Toast
import com.prestoza.inboxonly.appContainer
import com.prestoza.inboxonly.domain.TapFeedback
import com.prestoza.inboxonly.domain.TileStatus
import com.prestoza.inboxonly.domain.TileStatusFormatter
import com.prestoza.inboxonly.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Toggles Focus on tap and shows its status.
 *
 * Long-press opens the settings screen: Android sends `ACTION_QS_TILE_PREFERENCES`, which
 * MainActivity handles in the manifest. No code is needed here for that.
 */
class FocusTileService : TileService() {
    // Exists only while the shade is open. Cancelled in onStopListening so nothing runs afterwards.
    private var listeningScope: CoroutineScope? = null

    /** Called when the shade opens: render now, and re-render if Focus changes while it stays open. */
    override fun onStartListening() {
        super.onStartListening()
        val scope = MainScope()
        listeningScope = scope
        val container = appContainer
        scope.launch {
            combine(container.focusStateStore.state, container.settingsRepository.settings) { _, _ -> }
                .collect { render(currentStatus()) }
        }
    }

    /** Called when the shade closes. No ticking timers: the countdown is recomputed on the next open. */
    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    /**
     * Tap toggles Focus (or starts/ends a break inside a schedule window).
     *
     * If the accessibility service is off, Focus couldn't block anything, so the tap opens the app's
     * setup instead. (We can't use STATE_UNAVAILABLE for this: Android ignores taps on unavailable tiles.)
     */
    override fun onClick() {
        super.onClick()
        if (!AccessibilityStatus.isServiceEnabled(this)) {
            openApp()
            return
        }
        val container = appContainer
        container.applicationScope.launch {
            val transition = container.focusCoordinator.onTileTap()
            val status = currentStatus()
            withContext(Dispatchers.Main) {
                (transition.feedback as? TapFeedback.StrictModeLocked)?.let { showStrictModeToast(it) }
                render(status)
            }
        }
    }

    private suspend fun currentStatus(): TileStatus {
        val container = appContainer
        val coordinator = container.focusCoordinator
        return TileStatusFormatter.status(
            state = coordinator.currentState(),
            now = container.timeSource.now(),
            zone = container.timeSource.zone(),
            blockedToday = coordinator.blockedToday(),
            setupNeeded = !AccessibilityStatus.isServiceEnabled(this),
            use24Hour = DateFormat.is24HourFormat(this),
        )
    }

    // Collapses the shade and opens the app. Android 14 replaced the Intent overload with a
    // PendingIntent one; the old one throws there.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun render(status: TileStatus) {
        // qsTile is null if the tile was removed from the shade in the meantime.
        val tile = qsTile ?: return
        tile.state = if (status.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = status.subtitle
        tile.updateTile()
    }

    private fun showStrictModeToast(feedback: TapFeedback.StrictModeLocked) {
        val timeSource = appContainer.timeSource
        val message =
            TileStatusFormatter.strictModeMessage(feedback.until, timeSource.zone(), DateFormat.is24HourFormat(this))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
