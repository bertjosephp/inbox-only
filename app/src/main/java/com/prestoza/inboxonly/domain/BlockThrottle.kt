// BlockThrottle: keeps blocking from fighting the user. A short per-app cooldown stops one screen
// change from triggering several actions, and if the same surface is blocked again right after an
// action, the next action in the list is used (e.g. Back didn't leave the screen, so go Home).
// The block screen counts as the first step: if the surface is back right after it was shown, the
// screen didn't take, so the actions run directly.
package com.prestoza.inboxonly.domain

import java.time.Duration
import java.time.Instant

/** The throttle's answer for one Block decision. */
sealed interface BlockPlan {
    /** Too soon after the last action in this app. Check the screen again after [retryAfter]. */
    data class Cooldown(
        val retryAfter: Duration,
    ) : BlockPlan

    /**
     * Try these actions in order; stop at the first that works. When [escalated] is false this is a
     * fresh block, which the service shows as the block screen; when true, an earlier attempt didn't
     * leave the surface, so the actions run directly.
     */
    data class Execute(
        val actions: List<BlockAction>,
        val escalated: Boolean = false,
    ) : BlockPlan
}

/**
 * Per-app cooldown and loop escalation for block actions. Holds only timestamps, in memory.
 *
 * @param cooldown minimum time between two actions in the same app.
 * @param loopWindow if the same surface is blocked again within this time, the previous action didn't
 *   work, so escalate to the next one.
 */
class BlockThrottle(
    private val cooldown: Duration = Duration.ofMillis(700),
    private val loopWindow: Duration = Duration.ofSeconds(2),
) {
    // A null action means the block screen was shown.
    private data class LastAction(
        val surfaceId: String,
        val action: BlockAction?,
        val at: Instant,
    )

    private val lastByPackage = mutableMapOf<String, LastAction>()

    /** Plans how to handle [block] in [packageName] at [now]. */
    fun plan(
        packageName: String,
        block: Decision.Block,
        now: Instant,
    ): BlockPlan {
        val actions = withHomeFallback(block.actions)
        val last = lastByPackage[packageName] ?: return BlockPlan.Execute(actions)
        val sinceLast = Duration.between(last.at, now)
        if (sinceLast < cooldown) return BlockPlan.Cooldown(cooldown - sinceLast)

        val sameSurfaceAgain = last.surfaceId == block.surfaceId && sinceLast < loopWindow
        if (!sameSurfaceAgain) return BlockPlan.Execute(actions)
        val next = last.action?.let { (actions.indexOf(it) + 1).coerceAtMost(actions.lastIndex) } ?: 0
        return BlockPlan.Execute(actions.drop(next), escalated = true)
    }

    /** Records that [action] was performed for [surfaceId] in [packageName]. */
    fun recordExecuted(
        packageName: String,
        surfaceId: String,
        action: BlockAction,
        now: Instant,
    ) {
        lastByPackage[packageName] = LastAction(surfaceId, action, now)
    }

    /** Records that the block screen was shown for [surfaceId] in [packageName]. */
    fun recordScreenShown(
        packageName: String,
        surfaceId: String,
        now: Instant,
    ) {
        lastByPackage[packageName] = LastAction(surfaceId, null, now)
    }

    // Home always works and can't loop, so it's the guaranteed last resort.
    private fun withHomeFallback(actions: List<BlockAction>): List<BlockAction> =
        actions.filter { it != BlockAction.Home } + BlockAction.Home
}
