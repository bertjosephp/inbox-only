// Small helpers for AccessibilityNodeInfo, the read-only snapshot of one on-screen view that the
// accessibility framework hands us.
package com.prestoza.inboxonly.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Returns the node to the system's pool. Before Android 13, nodes were pooled and had to be recycled
 * by hand to avoid leaks; from 13 on, recycling is automatic and this is a no-op.
 */
@Suppress("DEPRECATION")
fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}
