package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The block screen's wording, for the shipped rules (see [FixtureScreens]). */
class BlockScreenFormatterTest {
    private fun screenFor(screen: ScreenSignature): BlockScreen {
        val decision = BlockDecider.decide(screen, FixtureScreens.rules, Settings(), ReelFromChatTracker())
        val block = decision as Decision.Block
        val appId = requireNotNull(FixtureScreens.rules.forPackage(screen.packageName)).appId
        return requireNotNull(BlockScreenFormatter.forBlock(appId, block.surfaceId, block.actions))
    }

    @Test
    fun `instagram feed offers the DM inbox and closing instagram`() {
        val screen = screenFor(FixtureScreens.igHomeFeed)
        assertEquals("Instagram", screen.appLabel)
        assertEquals("Home feed is blocked", screen.title)
        assertEquals("Open DMs", screen.primaryLabel)
        assertTrue(screen.primaryAction is BlockAction.Redirect)
        assertTrue(screen.showClose)
        assertEquals("Close Instagram", screen.closeLabel)
    }

    @Test
    fun `facebook feed offers messenger`() {
        val screen = screenFor(FixtureScreens.fbFeed)
        assertEquals("News feed is blocked", screen.title)
        assertEquals("Open Messenger", screen.primaryLabel)
    }

    @Test
    fun `shorts offers going back`() {
        val screen = screenFor(FixtureScreens.ytShorts)
        assertEquals("Shorts is blocked", screen.title)
        assertEquals("Go back", screen.primaryLabel)
        assertTrue(screen.showClose)
    }

    @Test
    fun `reddit and youtube home only offer closing the app`() {
        listOf(FixtureScreens.redditHome to "Close Reddit", FixtureScreens.ytHome to "Close YouTube")
            .forEach { (signature, label) ->
                val screen = screenFor(signature)
                assertEquals(label, screen.primaryLabel)
                assertEquals(BlockAction.Home, screen.primaryAction)
                assertFalse(screen.showClose)
            }
    }

    @Test
    fun `an action without a label gets a neutral one`() {
        val screen = BlockScreenFormatter.forBlock("instagram", "explore", listOf(BlockAction.Launch("p")))
        assertEquals("Continue", screen?.primaryLabel)
    }

    @Test
    fun `unknown app has no screen`() {
        assertNull(BlockScreenFormatter.forBlock("tiktok", "feed", listOf(BlockAction.Home)))
    }
}
