// ScreenSignatureReader: builds a ScreenSignature from the live screen. It visits the window's nodes
// once and keeps only what rules.json mentions for this app: which referenced view IDs and content
// descriptions are present, and whether they're selected. No text is kept.
//
// Why a walk and not findAccessibilityNodeInfosByViewId: Reddit (and parts of Instagram and YouTube)
// are built with Jetpack Compose, whose view IDs are bare tags like "home_screen_surface" rather than
// real "package:id/name" resources. The framework's ID lookup resolves real resources only, so it can't
// find those. See docs/DECISIONS.md (ADR-016).
package com.prestoza.inboxonly.service

import android.view.accessibility.AccessibilityNodeInfo
import com.prestoza.inboxonly.domain.AppRules
import com.prestoza.inboxonly.domain.ScreenSignature

/** Reads the minimal, content-free [ScreenSignature] that [com.prestoza.inboxonly.domain.BlockDecider] needs. */
object ScreenSignatureReader {
    // Real screens in the target apps have a few hundred nodes; the cap bounds the cost of a pathological one.
    private const val MAX_NODES = 2_000

    /** Builds the signature for [root], matching only what [app]'s rules reference. */
    fun read(
        root: AccessibilityNodeInfo,
        packageName: String,
        windowClass: String?,
        app: AppRules,
    ): ScreenSignature {
        val wantedIds = app.referencedViewIds
        val wantedDescriptions = app.referencedContentDescriptions
        val identityViewId = app.reelViewer?.identityViewId

        val viewIds = mutableSetOf<String>()
        val selectedViewIds = mutableSetOf<String>()
        val descriptions = mutableSetOf<String>()
        val selectedDescriptions = mutableSetOf<String>()
        var reelIdentity: Int? = null

        forEachNode(root) { node ->
            val id = node.viewIdResourceName
            if (id != null && id in wantedIds) {
                viewIds += id
                if (node.isSelected) selectedViewIds += id
            }
            // An opaque hash of the Reel's identifying label (e.g. its author), so a swipe to another Reel
            // can be noticed. Only the hash is kept, in memory; it's never logged or stored.
            if (id != null && id == identityViewId && reelIdentity == null) {
                reelIdentity = (node.contentDescription ?: node.text)?.toString()?.hashCode()
            }
            val description = node.contentDescription?.toString()
            if (description != null && description in wantedDescriptions) {
                descriptions += description
                if (node.isSelected) selectedDescriptions += description
            }
        }

        return ScreenSignature(
            packageName = packageName,
            windowClass = windowClass,
            viewIds = viewIds,
            selectedViewIds = selectedViewIds,
            contentDescriptions = descriptions,
            selectedContentDescriptions = selectedDescriptions,
            reelIdentity = reelIdentity,
        )
    }

    // Depth-first over the window, without recursion. The caller owns (and recycles) the root; every
    // child fetched here is recycled here.
    private inline fun forEachNode(
        root: AccessibilityNodeInfo,
        visit: (AccessibilityNodeInfo) -> Unit,
    ) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            try {
                if (visited++ >= MAX_NODES) continue
                visit(node)
                for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
            } finally {
                if (node !== root) node.recycleCompat()
            }
        }
    }
}
