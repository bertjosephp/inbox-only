// BlockDecider: the core rule. Given what's on screen, the rules for that app, the user's settings and
// the chat→Reel history, decide Allow or Block. Pure Kotlin, and it fails open: anything not clearly
// recognised as a blocked surface is allowed, so messaging can't be blocked by accident.
package com.prestoza.inboxonly.domain

/** The outcome for one screen. */
sealed interface Decision {
    /** Leave the user alone. */
    data object Allow : Decision

    /** Leave this screen, trying [actions] in order. [surfaceId] is for logs and counting. */
    data class Block(
        val surfaceId: String,
        val actions: List<BlockAction>,
    ) : Decision
}

/** Decides whether a screen should be blocked. */
object BlockDecider {
    /**
     * Decides for one screen while Focus is on.
     *
     * Order matters:
     * 1. No rules, or placeholder rules → Allow.
     * 2. Any always-allowed screen (DM inbox, chat thread...) → Allow. These win over everything.
     * 3. The Reel viewer → the [tracker] decides (allow one Reel opened from a chat).
     * 4. The first matching surface → Block if the user has it switched on, otherwise Allow.
     * 5. Nothing matched → Allow.
     *
     * @param tracker this app's chat→Reel state; updated as a side effect.
     */
    fun decide(
        signature: ScreenSignature,
        rules: Rules,
        settings: Settings,
        tracker: ReelFromChatTracker,
    ): Decision {
        if (rules.placeholder) return Decision.Allow
        val app = rules.forPackage(signature.packageName) ?: return Decision.Allow

        app.allowed.firstOrNull { it.match.matches(signature) }?.let { screen ->
            tracker.onAllowedScreen(isChat = app.reelViewer?.openedFrom?.contains(screen.id) == true)
            return Decision.Allow
        }

        app.reelViewer?.takeIf { it.match.matches(signature) }?.let { viewer ->
            val allowOne = settings.reelsFromChats == ReelsFromChats.ALLOW_ONE
            val allowedByTracker = tracker.onReelViewer(signature.reelIdentity, allowOne)
            val blocked = !allowedByTracker && settings.isSurfaceBlocked(app.appId, viewer.surfaceId)
            return if (blocked) Decision.Block(viewer.surfaceId, viewer.actions) else Decision.Allow
        }

        app.surfaces.firstOrNull { it.match.matches(signature) }?.let { surface ->
            tracker.onOtherRecognisedScreen()
            return if (settings.isSurfaceBlocked(app.appId, surface.id)) {
                Decision.Block(surface.id, surface.actions)
            } else {
                Decision.Allow
            }
        }

        return Decision.Allow
    }

    /**
     * A new window opened in [packageName]. If it's one that only opens from a chat in another app
     * ([ReelViewerRule.openedFromWindowClasses]), the next Reel counts as opened from a chat.
     *
     * Called on the window-state event itself, because the window's content (and even the active
     * window) may not be ready yet when that event arrives.
     */
    fun onWindowOpened(
        packageName: String,
        windowClass: String,
        rules: Rules,
        tracker: ReelFromChatTracker,
    ) {
        val viewer = rules.forPackage(packageName)?.reelViewer ?: return
        if (windowClass in viewer.openedFromWindowClasses) tracker.onAllowedScreen(isChat = true)
    }
}
