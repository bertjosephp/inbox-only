// BlockDecider: the core rule. Given what's on screen, the rules for that app, the user's settings and
// the chat→Reel history, decide Allow or Block. Pure Kotlin, and it fails open: anything not clearly
// recognised as a blocked surface is allowed, so messaging can't be blocked by accident.
package com.prestoza.inboxonly.domain

import java.time.Instant

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
     * 2. Moving on to a different item on a [FollowOnRule] page (another YouTube video) → Block. The one
     *    exception to "allowed screens win": the page itself is allowed, only its second item isn't.
     * 3. Any always-allowed screen (DM inbox, chat thread...) → Allow.
     * 4. The Reel viewer → the session's Reel tracker decides (allow one Reel opened from a chat).
     * 5. The first matching surface → Block if the user has it switched on, otherwise Allow.
     * 6. Nothing matched → Allow.
     *
     * @param session this app's history (chat→Reel, the current visit); updated as a side effect.
     */
    fun decide(
        signature: ScreenSignature,
        rules: Rules,
        settings: Settings,
        session: AppSession,
        now: Instant,
    ): Decision {
        if (rules.placeholder) return Decision.Allow
        val app = rules.forPackage(signature.packageName) ?: return Decision.Allow
        val tracker = session.reels

        app.followOn?.let { rule ->
            if (rule.match.matches(signature)) {
                val movedOn = session.followOn.onPage(signature.itemIdentity, now)
                if (movedOn && settings.isSurfaceBlocked(app.appId, rule.surfaceId)) {
                    return Decision.Block(rule.surfaceId, rule.actions)
                }
            } else {
                session.followOn.endVisit()
            }
        }

        app.allowed.firstOrNull { it.match.matches(signature) }?.let { screen ->
            tracker.onAllowedScreen(isChat = app.reelViewer?.openedFrom?.contains(screen.id) == true)
            return Decision.Allow
        }

        app.reelViewer?.takeIf { it.match.matches(signature) }?.let { viewer ->
            val allowOne = settings.reelsFromChats == ReelsFromChats.ALLOW_ONE
            val allowedByTracker = tracker.onReelViewer(signature.itemIdentity, allowOne)
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
     * ([ReelViewerRule.openedFromWindowClasses]), the next Reel counts as opened from a chat. If it's a
     * [FollowOnRule.resetWindowClasses] window (e.g. a link opening the app), a new visit starts.
     *
     * Called on the window-state event itself, because the window's content (and even the active
     * window) may not be ready yet when that event arrives.
     */
    fun onWindowOpened(
        packageName: String,
        windowClass: String,
        rules: Rules,
        session: AppSession,
    ) {
        val app = rules.forPackage(packageName) ?: return
        val viewer = app.reelViewer
        if (viewer != null && windowClass in viewer.openedFromWindowClasses) {
            session.reels.onAllowedScreen(isChat = true)
        }
        if (app.followOn?.resetWindowClasses?.contains(windowClass) == true) session.followOn.endVisit()
    }
}
