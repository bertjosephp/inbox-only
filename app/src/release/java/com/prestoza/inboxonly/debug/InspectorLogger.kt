// InspectorLogger (release builds): a no-op twin of the debug inspector. Release builds never log
// anything, and with ENABLED = false the accessibility service stays fully inert while Focus is off.
package com.prestoza.inboxonly.debug

import android.view.accessibility.AccessibilityNodeInfo
import com.prestoza.inboxonly.domain.Decision
import com.prestoza.inboxonly.domain.ScreenSignature

/** Does nothing in release builds. */
object InspectorLogger {
    /** Always false in release builds. */
    const val ENABLED = false

    /** No-op in release builds. */
    @Suppress("UNUSED_PARAMETER")
    fun logScreen(
        root: AccessibilityNodeInfo,
        packageName: String,
        windowClass: String?,
    ) = Unit

    /** No-op in release builds. */
    @Suppress("UNUSED_PARAMETER")
    fun logDecision(
        signature: ScreenSignature,
        decision: Decision,
    ) = Unit
}
