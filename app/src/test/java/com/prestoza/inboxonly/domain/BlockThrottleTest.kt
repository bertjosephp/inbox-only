package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BlockThrottleTest {
    private val throttle = BlockThrottle()
    private val t0 = Instant.parse("2026-10-29T12:00:00Z")
    private val pkg = "com.example.target"
    private val redirect = BlockAction.Redirect("fixture://inbox")
    private val feed = Decision.Block("home_feed", listOf(redirect, BlockAction.Back))

    private fun ms(millis: Long): Instant = t0.plusMillis(millis)

    @Test
    fun `first block runs the full action list with home appended`() {
        assertEquals(
            BlockPlan.Execute(listOf(redirect, BlockAction.Back, BlockAction.Home)),
            throttle.plan(pkg, feed, t0),
        )
    }

    @Test
    fun `home is not duplicated when already last`() {
        val block = Decision.Block("x", listOf(BlockAction.Home))
        assertEquals(BlockPlan.Execute(listOf(BlockAction.Home)), throttle.plan(pkg, block, t0))
    }

    @Test
    fun `a second block within the cooldown waits and says how long`() {
        throttle.recordExecuted(pkg, "home_feed", redirect, t0)
        assertEquals(BlockPlan.Cooldown(Duration.ofMillis(500)), throttle.plan(pkg, feed, ms(200)))
    }

    @Test
    fun `cooldown is per app`() {
        throttle.recordExecuted(pkg, "home_feed", redirect, t0)
        assertEquals(3, (throttle.plan("com.other", feed, ms(100)) as BlockPlan.Execute).actions.size)
    }

    @Test
    fun `same surface again right after an action escalates to the next action`() {
        throttle.recordExecuted(pkg, "home_feed", redirect, t0)
        assertEquals(
            BlockPlan.Execute(listOf(BlockAction.Back, BlockAction.Home), escalated = true),
            throttle.plan(pkg, feed, ms(900)),
        )
        throttle.recordExecuted(pkg, "home_feed", BlockAction.Back, ms(900))
        assertEquals(BlockPlan.Execute(listOf(BlockAction.Home), escalated = true), throttle.plan(pkg, feed, ms(1800)))
    }

    @Test
    fun `back that loops escalates to home`() {
        val reels = Decision.Block("reels", listOf(BlockAction.Back))
        throttle.recordExecuted(pkg, "reels", BlockAction.Back, t0)
        assertEquals(BlockPlan.Execute(listOf(BlockAction.Home), escalated = true), throttle.plan(pkg, reels, ms(800)))
    }

    @Test
    fun `a different surface right after an action starts fresh`() {
        // Back out of Reels lands on the feed: the feed gets its own redirect, not an escalation.
        throttle.recordExecuted(pkg, "reels", BlockAction.Back, t0)
        assertEquals(
            BlockPlan.Execute(listOf(redirect, BlockAction.Back, BlockAction.Home)),
            throttle.plan(pkg, feed, ms(800)),
        )
    }

    @Test
    fun `the block screen also gets the cooldown`() {
        throttle.recordScreenShown(pkg, "home_feed", t0)
        assertEquals(BlockPlan.Cooldown(Duration.ofMillis(400)), throttle.plan(pkg, feed, ms(300)))
    }

    @Test
    fun `same surface right after the block screen runs the actions directly`() {
        throttle.recordScreenShown(pkg, "home_feed", t0)
        assertEquals(
            BlockPlan.Execute(listOf(redirect, BlockAction.Back, BlockAction.Home), escalated = true),
            throttle.plan(pkg, feed, ms(900)),
        )
    }

    @Test
    fun `back from the block screen that loops escalates to home`() {
        val reels = Decision.Block("reels", listOf(BlockAction.Back))
        throttle.recordScreenShown(pkg, "reels", t0)
        throttle.recordExecuted(pkg, "reels", BlockAction.Back, ms(3000))
        assertEquals(BlockPlan.Execute(listOf(BlockAction.Home), escalated = true), throttle.plan(pkg, reels, ms(3800)))
    }

    @Test
    fun `same surface after the loop window starts fresh`() {
        throttle.recordExecuted(pkg, "home_feed", BlockAction.Back, t0)
        assertEquals(3, (throttle.plan(pkg, feed, ms(5000)) as BlockPlan.Execute).actions.size)
    }
}
