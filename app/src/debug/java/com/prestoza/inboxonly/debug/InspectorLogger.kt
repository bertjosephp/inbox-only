// InspectorLogger (debug builds only): prints a compact outline of the current screen in a target app
// to Logcat, under the tag InboxOnlyInspector. This is how real view IDs are captured to write
// rules.json; see docs/UPDATING_RULES.md. Release builds use a no-op twin in src/release.
//
// Privacy: only the four target apps are ever seen (the service's package filter), any text or
// description over 30 characters is replaced by its length, and nothing is stored or sent anywhere.
package com.prestoza.inboxonly.debug

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.prestoza.inboxonly.domain.Decision
import com.prestoza.inboxonly.domain.ScreenSignature
import com.prestoza.inboxonly.service.recycleCompat

/** Logs screen outlines for writing rules. Debug builds only. */
object InspectorLogger {
    /** True in debug builds: the service then listens even while Focus is off, so screens can be captured. */
    const val ENABLED = true

    private const val TAG = "InboxOnlyInspector"
    private const val MAX_NODES = 400
    private const val MAX_DEPTH = 40

    // Content-change events repeat the same screen many times; only log when the outline differs.
    private var lastOutlineHash = 0

    /** Logs the outline of [root] unless it's identical to the last one logged. */
    fun logScreen(
        root: AccessibilityNodeInfo,
        packageName: String,
        windowClass: String?,
    ) {
        val lines = mutableListOf<String>()
        collect(root, depth = 0, lines = lines, isRoot = true)
        val outline = lines.joinToString("\n")
        val hash = (packageName + windowClass + outline).hashCode()
        if (hash == lastOutlineHash) return
        lastOutlineHash = hash

        Log.d(TAG, "===== $packageName | window=${windowClass ?: "?"} | ${lines.size} nodes =====")
        // Logcat truncates long entries, so log line by line.
        lines.forEach { Log.d(TAG, it) }
        Log.d(TAG, "===== end =====")
    }

    /**
     * Logs what the rules saw and decided, so a rule that doesn't fire can be debugged on device.
     * Signatures hold only rule-referenced IDs and descriptions; the Reel identity is shown as present/absent.
     */
    fun logDecision(
        signature: ScreenSignature,
        decision: Decision,
    ) {
        val reel = if (signature.reelIdentity != null) "present" else "none"
        val summary =
            "ids=${signature.viewIds.sorted()} selected=${signature.selectedViewIds.sorted()} " +
                "descs=${signature.contentDescriptions.sorted()} " +
                "selectedDescs=${signature.selectedContentDescriptions.sorted()} reelIdentity=$reel"
        Log.d(TAG, "decision ${signature.packageName}: $decision | $summary")
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        lines: MutableList<String>,
        isRoot: Boolean = false,
    ) {
        try {
            if (lines.size >= MAX_NODES || depth > MAX_DEPTH) return
            InspectorFormat
                .nodeLine(
                    depth = depth,
                    className = node.className,
                    viewId = node.viewIdResourceName,
                    contentDescription = node.contentDescription,
                    text = node.text,
                    selected = node.isSelected,
                )?.let(lines::add)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collect(it, depth + 1, lines) }
            }
        } finally {
            // The caller owns the root; we own (and must recycle) every child we fetched.
            if (!isRoot) node.recycleCompat()
        }
    }
}
