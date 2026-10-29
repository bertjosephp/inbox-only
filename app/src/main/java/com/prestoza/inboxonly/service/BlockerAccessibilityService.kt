// BlockerAccessibilityService: the app's eyes. An AccessibilityService is a system-bound component
// (normally used by screen readers) that receives events when other apps' screens change and can read
// their view hierarchy and perform global actions like Back. We use it only to notice which screen is
// showing in four target apps. It stays thin: it observes, and later asks pure domain code what to do.
package com.prestoza.inboxonly.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.prestoza.inboxonly.appContainer
import com.prestoza.inboxonly.debug.InspectorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Watches the four target apps while Focus is on.
 *
 * Battery: the XML config limits events to four packages and two event types; when Focus is off we
 * clear the event types so the system delivers nothing at all. Content-change events are throttled.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private val handler = Handler(Looper.getMainLooper())

    // Content-changed events don't say which activity is showing; remember it from the last
    // window-state event per package.
    private val windowClassByPackage = mutableMapOf<String, String>()

    private var pendingPackage: String? = null
    private val pendingCheck = Runnable { pendingPackage?.let(::onScreenMaybeChanged) }

    /** Called when the system binds the service (user enabled it, boot, or after the OS restarted it). */
    override fun onServiceConnected() {
        super.onServiceConnected()
        val newScope = MainScope()
        scope = newScope
        newScope.launch {
            appContainer.focusStateStore.state
                .map { it.isBlocking }
                .distinctUntilChanged()
                .collect { blocking -> setListening(blocking || InspectorLogger.ENABLED) }
        }
    }

    /** Receives events from the target apps only, and only while listening. */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.className?.let { windowClassByPackage[packageName] = it.toString() }
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
        handler.removeCallbacks(pendingCheck)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun onScreenMaybeChanged(packageName: String) {
        val root = rootInActiveWindow ?: return
        try {
            // The active window can belong to another app (e.g. a system dialog); ignore those.
            if (root.packageName?.toString() != packageName) return
            InspectorLogger.logScreen(root, packageName, windowClassByPackage[packageName])
        } finally {
            root.recycleCompat()
        }
    }

    /**
     * Turns event delivery on or off at runtime.
     *
     * Only `eventTypes` changes. `packageNames` must never be set to null: null means "all apps", the
     * opposite of what we want. It keeps the four packages from the XML config.
     */
    private fun setListening(listening: Boolean) {
        val info = serviceInfo ?: return
        info.eventTypes = if (listening) LISTENED_EVENT_TYPES else 0
        serviceInfo = info
        if (!listening) {
            handler.removeCallbacks(pendingCheck)
            pendingPackage = null
        }
    }

    private companion object {
        const val CONTENT_THROTTLE_MS = 250L
        const val LISTENED_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
}
