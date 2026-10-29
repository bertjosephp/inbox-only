// BlockerAccessibilityService: the app's eyes and hands. An AccessibilityService is a system-bound
// component (normally used by screen readers) that receives events when other apps' screens change, can
// look up views on screen, and can perform global actions like Back and Home. It stays thin: it builds a
// ScreenSignature, asks the pure BlockDecider, and carries out the answer.
package com.prestoza.inboxonly.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.net.toUri
import com.prestoza.inboxonly.appContainer
import com.prestoza.inboxonly.debug.InspectorLogger
import com.prestoza.inboxonly.domain.AppSession
import com.prestoza.inboxonly.domain.BlockAction
import com.prestoza.inboxonly.domain.BlockDecider
import com.prestoza.inboxonly.domain.BlockPlan
import com.prestoza.inboxonly.domain.BlockThrottle
import com.prestoza.inboxonly.domain.Decision
import com.prestoza.inboxonly.domain.FocusController
import com.prestoza.inboxonly.domain.FocusState
import com.prestoza.inboxonly.domain.Settings
import com.prestoza.inboxonly.ui.BlockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Blocks doomscroll surfaces in the four target apps while Focus is on.
 *
 * Battery: the XML config limits events to four packages and two event types; when Focus is off we
 * clear the event types so the system delivers nothing at all. Content-change events are throttled.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private val handler = Handler(Looper.getMainLooper())

    // Latest values from the stores, cached so each screen check is synchronous and cheap.
    private var storedState: FocusState = FocusState.Off
    private var settings = Settings()

    // Content-changed events don't say which activity is showing; remember it from the last
    // window-state event per package.
    private val windowClassByPackage = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, AppSession>()
    private val throttle = BlockThrottle()

    private var pendingPackage: String? = null
    private val pendingCheck = Runnable { pendingPackage?.let(::onScreenMaybeChanged) }

    /** Called when the system binds the service (user enabled it, boot, or after the OS restarted it). */
    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = appContainer
        // The block screen's "Go back" can only work once the screen has gone, so wait for its exit.
        container.blockScreenBridge.goBack = { packageName, surfaceId ->
            handler.postDelayed({ goBackAfterBlockScreen(packageName, surfaceId) }, BACK_AFTER_SCREEN_MS)
        }
        val newScope = MainScope()
        scope = newScope
        // Parse rules.json off the main thread so the first screen check doesn't pay for it.
        newScope.launch(Dispatchers.IO) { container.rulesRepository.rules }
        newScope.launch {
            combine(container.focusStateStore.state, container.settingsRepository.settings, ::Pair)
                .collect { (state, latestSettings) ->
                    storedState = state
                    settings = latestSettings
                    setListening(state.isBlocking || InspectorLogger.ENABLED)
                }
        }
    }

    /** Receives events from the target apps only, and only while listening. */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.className?.toString()?.let { windowClass ->
                    windowClassByPackage[packageName] = windowClass
                    BlockDecider.onWindowOpened(
                        packageName,
                        windowClass,
                        appContainer.rulesRepository.rules,
                        session(packageName),
                    )
                }
                handler.removeCallbacks(pendingCheck)
                pendingPackage = null
                onScreenMaybeChanged(packageName)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Throttle, not debounce: a playing video changes content constantly, and a pure debounce
                // would never fire. At most one check per window; extra events inside it are dropped.
                pendingPackage = packageName
                if (!handler.hasCallbacks(pendingCheck)) handler.postDelayed(pendingCheck, CONTENT_THROTTLE_MS)
            }

            // Not subscribed to anything else; ignore whatever the system might still deliver.
            else -> {
                Unit
            }
        }
    }

    /** Required by the API; we provide no spoken or haptic feedback, so there's nothing to interrupt. */
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        appContainer.blockScreenBridge.goBack = null
        handler.removeCallbacksAndMessages(null)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun onScreenMaybeChanged(packageName: String) {
        val root = rootInActiveWindow ?: return
        try {
            // The active window can belong to another app (e.g. a system dialog); ignore those.
            if (root.packageName?.toString() != packageName) return
            val windowClass = windowClassByPackage[packageName]
            InspectorLogger.logScreen(root, packageName, windowClass)
            if (!isFocusBlocking()) return

            val rules = appContainer.rulesRepository.rules
            val app = rules.forPackage(packageName)
            if (rules.placeholder || app == null) return
            val signature = ScreenSignatureReader.read(root, packageName, windowClass, app)
            val now = appContainer.timeSource.now()
            val decision = BlockDecider.decide(signature, rules, settings, session(packageName), now)
            InspectorLogger.logDecision(signature, decision)
            if (decision is Decision.Block) block(packageName, app.appId, decision)
        } finally {
            root.recycleCompat()
        }
    }

    private fun session(packageName: String): AppSession = sessions.getOrPut(packageName) { AppSession() }

    // The stored state can be stale if an alarm is late, so resolve it against the clock every time.
    private fun isFocusBlocking(): Boolean {
        val time = appContainer.timeSource
        return FocusController.reconcile(storedState, settings, time.now(), time.zone()).isBlocking
    }

    private fun block(
        packageName: String,
        appId: String,
        decision: Decision.Block,
    ) {
        val now = appContainer.timeSource.now()
        when (val plan = throttle.plan(packageName, decision, now)) {
            is BlockPlan.Cooldown -> {
                // Cooldown so we never trap the user in a back-button loop. Look again once it's over, in
                // case the screen is static and sends no more events.
                pendingPackage = packageName
                handler.removeCallbacks(pendingCheck)
                handler.postDelayed(pendingCheck, plan.retryAfter.toMillis() + COOLDOWN_RECHECK_SLACK_MS)
            }

            is BlockPlan.Execute -> {
                // A fresh block shows the block screen; its buttons offer the way out. If the surface is
                // back right after that (or after an action), act directly so the user can't get stuck.
                val intent = BlockScreenActivity.intent(this, packageName, appId, decision)
                // No window animation: the page draws its own fade + zoom (see BlockScreenActivity).
                if (!plan.escalated && startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))) {
                    throttle.recordScreenShown(packageName, decision.surfaceId, now)
                } else {
                    val performed = plan.actions.firstOrNull(::perform) ?: return
                    throttle.recordExecuted(packageName, decision.surfaceId, performed, now)
                }
                // One count per block event, not per accessibility event.
                appContainer.applicationScope.launch { appContainer.focusCoordinator.recordBlock() }
            }
        }
    }

    private fun goBackAfterBlockScreen(
        packageName: String,
        surfaceId: String,
    ) {
        if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            throttle.recordExecuted(packageName, surfaceId, BlockAction.Back, appContainer.timeSource.now())
        }
    }

    /** Carries out one action. Returns false if it couldn't (e.g. a deep link no longer resolves). */
    private fun perform(action: BlockAction): Boolean =
        when (action) {
            BlockAction.Back -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            BlockAction.Home -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            is BlockAction.Redirect -> {
                val intent = Intent(Intent.ACTION_VIEW, action.uri.toUri())
                action.packageName?.let(intent::setPackage)
                startSafely(intent)
            }

            is BlockAction.Launch -> {
                packageManager.getLaunchIntentForPackage(action.packageName)?.let(::startSafely) ?: false
            }
        }

    // Accessibility services may start activities from the background: the system binds them, which
    // exempts them from Android's background-activity-launch restrictions.
    private fun startSafely(intent: Intent): Boolean =
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    /**
     * Turns event delivery on or off at runtime.
     *
     * Only `eventTypes` changes. `packageNames` must never be set to null: null means "all apps", the
     * opposite of what we want. It keeps the four packages from the XML config.
     */
    private fun setListening(listening: Boolean) {
        val info = serviceInfo ?: return
        val eventTypes = if (listening) LISTENED_EVENT_TYPES else 0
        if (info.eventTypes == eventTypes) return
        info.eventTypes = eventTypes
        serviceInfo = info
        if (!listening) {
            handler.removeCallbacks(pendingCheck)
            pendingPackage = null
        }
    }

    private companion object {
        const val CONTENT_THROTTLE_MS = 250L
        const val COOLDOWN_RECHECK_SLACK_MS = 50L

        // The page has faded out before it asks; this just lets its window go so Back reaches the app.
        const val BACK_AFTER_SCREEN_MS = 150L
        const val LISTENED_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
}
