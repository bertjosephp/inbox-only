package com.prestoza.inboxonly.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelFromChatTrackerTest {
    private val tracker = ReelFromChatTracker()

    private fun reel(
        identity: Int?,
        allowOne: Boolean = true,
    ) = tracker.onReelViewer(identity, allowOne)

    @Test
    fun `reel with no chat before it is blocked`() {
        assertFalse(reel(1))
    }

    @Test
    fun `first reel after a chat is allowed and keeps being allowed`() {
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(1))
        assertTrue(reel(1))
    }

    @Test
    fun `swiping to a different reel is blocked`() {
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(1))
        assertFalse(reel(2))
    }

    @Test
    fun `after a blocked swipe, further reels stay blocked until back in a chat`() {
        tracker.onAllowedScreen(isChat = true)
        reel(1)
        reel(2)
        assertFalse(reel(3))
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(3))
    }

    @Test
    fun `block setting refuses even the first reel`() {
        tracker.onAllowedScreen(isChat = true)
        assertFalse(reel(1, allowOne = false))
    }

    @Test
    fun `without reel identity the opened reel is allowed until the viewer is left`() {
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(null))
        assertTrue(reel(null))
        tracker.onAllowedScreen(isChat = false) // left to the inbox
        assertFalse(reel(null))
    }

    @Test
    fun `a non-chat allowed screen breaks the link`() {
        tracker.onAllowedScreen(isChat = true)
        tracker.onAllowedScreen(isChat = false)
        assertFalse(reel(1))
    }

    @Test
    fun `a recognised feed screen breaks the link`() {
        tracker.onAllowedScreen(isChat = true)
        tracker.onOtherRecognisedScreen()
        assertFalse(reel(1))
    }

    @Test
    fun `unrecognised transitional screens between chat and reel keep the link`() {
        // The decider doesn't call the tracker at all for unmatched screens.
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(7))
    }

    @Test
    fun `identity appearing late does not count as a swipe`() {
        tracker.onAllowedScreen(isChat = true)
        assertTrue(reel(null))
        assertTrue(reel(5))
    }
}
