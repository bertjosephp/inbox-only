// ScreenSignatureReader: builds a ScreenSignature from the live screen. It never walks the whole view
// tree: it asks the framework only for the specific view IDs and content descriptions that rules.json
// mentions for this app, and records whether they're present and selected. No text is kept.
package com.prestoza.inboxonly.service

import android.view.accessibility.AccessibilityNodeInfo
import com.prestoza.inboxonly.domain.AppRules
import com.prestoza.inboxonly.domain.ScreenSignature

/** Reads the minimal, content-free [ScreenSignature] that [com.prestoza.inboxonly.domain.BlockDecider] needs. */
object ScreenSignatureReader {
    /** Builds the signature for [root], looking up only what [app]'s rules reference. */
    fun read(
        root: AccessibilityNodeInfo,
        packageName: String,
        windowClass: String?,
        app: AppRules,
    ): ScreenSignature {
        val viewIds = mutableSetOf<String>()
        val selectedViewIds = mutableSetOf<String>()
        for (id in app.referencedViewIds) {
            withNodes(root.findAccessibilityNodeInfosByViewId(id)) { nodes ->
                if (nodes.isNotEmpty()) viewIds += id
                if (nodes.any { it.isSelected }) selectedViewIds += id
            }
        }

        val descriptions = mutableSetOf<String>()
        val selectedDescriptions = mutableSetOf<String>()
        for (description in app.referencedContentDescriptions) {
            // findAccessibilityNodeInfosByText matches text *or* content description, case-insensitively and
            // by substring, so keep only nodes whose content description is exactly the one we asked for.
            withNodes(root.findAccessibilityNodeInfosByText(description)) { nodes ->
                val exact = nodes.filter { it.contentDescription?.toString() == description }
                if (exact.isNotEmpty()) descriptions += description
                if (exact.any { it.isSelected }) selectedDescriptions += description
            }
        }

        return ScreenSignature(
            packageName = packageName,
            windowClass = windowClass,
            viewIds = viewIds,
            selectedViewIds = selectedViewIds,
            contentDescriptions = descriptions,
            selectedContentDescriptions = selectedDescriptions,
            reelIdentity = app.reelViewer?.identityViewId?.let { reelIdentity(root, it) },
        )
    }

    // An opaque hash of the Reel's identifying label (e.g. its author), so a swipe to another Reel can be
    // noticed. Only the hash leaves this function; it lives in memory and is never logged or stored.
    private fun reelIdentity(
        root: AccessibilityNodeInfo,
        viewId: String,
    ): Int? =
        withNodes(root.findAccessibilityNodeInfosByViewId(viewId)) { nodes ->
            nodes.firstNotNullOfOrNull { (it.contentDescription ?: it.text)?.toString()?.hashCode() }
        }

    private inline fun <T> withNodes(
        nodes: List<AccessibilityNodeInfo>,
        block: (List<AccessibilityNodeInfo>) -> T,
    ): T =
        try {
            block(nodes)
        } finally {
            nodes.forEach { it.recycleCompat() }
        }
}
