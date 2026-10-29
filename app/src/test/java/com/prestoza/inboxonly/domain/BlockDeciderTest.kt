package com.prestoza.inboxonly.domain

import com.prestoza.inboxonly.domain.FixtureScreens.FB
import com.prestoza.inboxonly.domain.FixtureScreens.IG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDeciderTest {
    private val rules = FixtureScreens.rules
    private val defaults = Settings()

    private fun decide(
        screen: ScreenSignature,
        settings: Settings = defaults,
        tracker: ReelFromChatTracker = ReelFromChatTracker(),
        rules: Rules = this.rules,
    ) = BlockDecider.decide(screen, rules, settings, tracker)

    private fun assertBlocked(
        surfaceId: String,
        screen: ScreenSignature,
    ) {
        val decision = decide(screen)
        assertTrue("expected $surfaceId to be blocked, got $decision", decision is Decision.Block)
        assertEquals(surfaceId, (decision as Decision.Block).surfaceId)
    }

    private fun assertAllowed(screen: ScreenSignature) = assertEquals(Decision.Allow, decide(screen))

    // ---- Per-app surfaces from the spec table ----

    @Test
    fun `instagram blocks reels tab, explore and home feed`() {
        assertBlocked("reels_tab", FixtureScreens.igReelsTab)
        assertBlocked("explore", FixtureScreens.igExplore)
        assertBlocked("home_feed", FixtureScreens.igHomeFeed)
    }

    @Test
    fun `instagram allows DMs, threads, notifications and search results`() {
        assertAllowed(FixtureScreens.igDmInbox)
        assertAllowed(FixtureScreens.igDmThread)
        assertAllowed(FixtureScreens.igNotifications)
        assertAllowed(FixtureScreens.igSearchResults)
    }

    @Test
    fun `instagram feed block redirects to the inbox before falling back to back`() {
        val decision = decide(FixtureScreens.igHomeFeed) as Decision.Block
        assertEquals(
            listOf(BlockAction.Redirect("fixture://dm-inbox", IG), BlockAction.Back),
            decision.actions,
        )
    }

    @Test
    fun `facebook blocks reels, video and feed and allows notifications`() {
        assertBlocked("reels", FixtureScreens.fbReels)
        assertBlocked("video", FixtureScreens.fbVideo)
        assertBlocked("news_feed", FixtureScreens.fbFeed)
        assertAllowed(FixtureScreens.fbNotifications)
    }

    @Test
    fun `facebook feed block opens messenger`() {
        val decision = decide(FixtureScreens.fbFeed) as Decision.Block
        assertEquals(listOf(BlockAction.Launch("com.facebook.orca")), decision.actions)
    }

    @Test
    fun `youtube blocks shorts and home and allows search, subscriptions and a watched video`() {
        assertBlocked("shorts", FixtureScreens.ytShorts)
        assertBlocked("home_feed", FixtureScreens.ytHome)
        assertAllowed(FixtureScreens.ytSearch)
        assertAllowed(FixtureScreens.ytSubscriptions)
        assertAllowed(FixtureScreens.ytWatch)
    }

    @Test
    fun `youtube home closes the app`() {
        assertEquals(listOf(BlockAction.Home), (decide(FixtureScreens.ytHome) as Decision.Block).actions)
    }

    @Test
    fun `reddit allows only a thread`() {
        assertAllowed(FixtureScreens.redditThread)
        assertBlocked("home_feed", FixtureScreens.redditHome)
        assertBlocked("popular", FixtureScreens.redditPopular)
        assertBlocked("subreddit_feed", FixtureScreens.redditSubreddit)
        assertBlocked("search_results", FixtureScreens.redditSearchResults)
        assertBlocked("video_feed", FixtureScreens.redditVideoFeed)
    }

    @Test
    fun `reddit opened from the launcher exits, and back out of a thread into a feed exits`() {
        // Launcher lands on Home; a thread from Google is allowed; Back from it lands on a feed.
        val tracker = ReelFromChatTracker()
        assertEquals(
            listOf(BlockAction.Home),
            (decide(FixtureScreens.redditHome, tracker = tracker) as Decision.Block).actions,
        )
        assertEquals(Decision.Allow, decide(FixtureScreens.redditThread, tracker = tracker))
        assertTrue(decide(FixtureScreens.redditSubreddit, tracker = tracker) is Decision.Block)
    }

    // ---- Fail open ----

    @Test
    fun `unrecognised screens are allowed`() {
        assertAllowed(ScreenSignature(IG, viewIds = setOf("fixture:id/something_new")))
        assertAllowed(ScreenSignature(FB))
    }

    @Test
    fun `apps without rules are allowed`() {
        assertAllowed(ScreenSignature("com.facebook.orca", viewIds = setOf("fixture:id/tab_reels")))
    }

    @Test
    fun `placeholder rules never block`() {
        val placeholder = rules.copy(placeholder = true)
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelsTab, rules = placeholder))
    }

    @Test
    fun `allowed screens win even when a blocked surface also matches`() {
        // A DM thread that happens to show the selected Home tab underneath.
        val overlap =
            FixtureScreens.igHomeFeed.copy(
                viewIds =
                    FixtureScreens.igHomeFeed.viewIds + "fixture:id/thread_composer",
            )
        assertAllowed(overlap)
    }

    @Test
    fun `an empty match never matches anything`() {
        val catchAll =
            Rules(1, apps = listOf(AppRules("instagram", IG, surfaces = listOf(SurfaceRule("home_feed", Match())))))
        assertEquals(Decision.Allow, decide(ScreenSignature(IG), rules = catchAll))
    }

    // ---- Settings ----

    @Test
    fun `a surface switched off by the user is allowed`() {
        val settings = defaults.copy(apps = mapOf("instagram" to AppBlocking(disabledSurfaces = setOf("explore"))))
        assertEquals(Decision.Allow, decide(FixtureScreens.igExplore, settings))
        assertTrue(decide(FixtureScreens.igReelsTab, settings) is Decision.Block)
    }

    @Test
    fun `an app switched off by the user is fully allowed`() {
        val settings = defaults.copy(apps = mapOf("reddit" to AppBlocking(appEnabled = false)))
        assertEquals(Decision.Allow, decide(FixtureScreens.redditHome, settings))
        assertEquals(Decision.Allow, decide(FixtureScreens.redditVideoFeed, settings))
    }

    // ---- Reels from chats ----

    @Test
    fun `reel opened from a chat is allowed, swiping to another sends you back`() {
        val tracker = ReelFromChatTracker()
        assertEquals(Decision.Allow, decide(FixtureScreens.igDmThread, tracker = tracker))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelFromChat(1), tracker = tracker))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelFromChat(1), tracker = tracker))
        val swiped = decide(FixtureScreens.igReelFromChat(2), tracker = tracker)
        assertEquals(Decision.Block("reels_tab", listOf(BlockAction.Back)), swiped)
    }

    @Test
    fun `reel viewer reached without a chat is blocked`() {
        val tracker = ReelFromChatTracker()
        decide(FixtureScreens.igDmInbox, tracker = tracker)
        assertTrue(decide(FixtureScreens.igReelFromChat(1), tracker = tracker) is Decision.Block)
    }

    @Test
    fun `reels from chats set to block blocks even the first reel`() {
        val tracker = ReelFromChatTracker()
        val settings = defaults.copy(reelsFromChats = ReelsFromChats.BLOCK)
        decide(FixtureScreens.igDmThread, settings, tracker)
        assertTrue(decide(FixtureScreens.igReelFromChat(1), settings, tracker) is Decision.Block)
    }

    @Test
    fun `reel viewer is allowed when the user allows reels in instagram`() {
        val settings = defaults.copy(apps = mapOf("instagram" to AppBlocking(disabledSurfaces = setOf("reels_tab"))))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelFromChat(1), settings))
    }

    @Test
    fun `rules only reference fixture identifiers`() {
        val ids = rules.apps.flatMap { it.referencedViewIds }
        assertTrue(ids.isNotEmpty())
        assertTrue(ids.all { it.startsWith("fixture:") })
        assertEquals(FB, rules.forPackage(FB)?.packageName)
    }
}
