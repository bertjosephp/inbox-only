// AccessibilityStatus: answers "has the user enabled our accessibility service, and is it running?".
// Without both, Focus can't block anything, so the tile shows "Set up" or "Restart blocker" and the
// settings footer offers a fix.
package com.prestoza.inboxonly.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.prestoza.inboxonly.domain.BlockerHealth

/** Checks whether [BlockerAccessibilityService] is enabled in system settings. */
object AccessibilityStatus {
    /** True when the user has switched our service on under Settings → Accessibility. */
    fun isServiceEnabled(context: Context): Boolean {
        val ours = ComponentName(context, BlockerAccessibilityService::class.java)
        // The system stores enabled services as one colon-separated string of component names.
        val enabled =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        return enabled
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == ours }
    }

    /**
     * True when Android is actually running our service. The system answers this list from the services
     * it has bound, not from the Settings switch, so it's false when an update left the service stopped.
     */
    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val ours = ComponentName(context, BlockerAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == ours } }
    }

    /** Combines both checks. */
    fun health(context: Context): BlockerHealth =
        when {
            !isServiceEnabled(context) -> BlockerHealth.NOT_ENABLED
            !isServiceRunning(context) -> BlockerHealth.STOPPED
            else -> BlockerHealth.RUNNING
        }
}
