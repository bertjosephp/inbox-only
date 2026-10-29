// AccessibilityStatus: answers "has the user enabled our accessibility service?". Without it Focus
// can't block anything, so the tile shows "Set up" and the settings footer offers a fix.
package com.prestoza.inboxonly.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

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
}
