package com.prestoza.inboxonly.domain

import com.prestoza.inboxonly.domain.FixtureScreens.IG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Runs the shipped rules.json against real captured screen signatures (see [FixtureScreens]). */
class BlockDeciderTest {
    private val rules = FixtureScreens.rules
    private val defaults = Settings()

    private fun decide(
        screen: ScreenSignature,
        settings: Settings = defaults,
        tracker: ReelFromChatTracker = ReelFromChatTracker(),
        rules: Rules = this.rules,
        session: AppSession = AppSession(reels = tracker),
        now: Instant = t0,
    ) = BlockDecider.decide(screen, rules, settings, session, now)

    private val t0 = Instant.parse("2026-10-29T12:00:00Z")

    private fun seconds(s: Long): Instant = t0.plusSeconds(s)

    private fun assertBlocked(
        surfaceId: String,
        screen: ScreenSignature,
    ) {
        val decision = decide(screen)
        assertTrue("expected $surfaceId to be blocked, got $decision", decision is Decision.Block)
        assertEquals(surfaceId, (decision as Decision.Block).surfaceId)
    }

    private fun assertAllowed(screen: ScreenSignature) = assertEquals(Decision.Allow, decide(screen))

    private val toInbox = BlockAction.Redirect("instagram://direct-inbox", IG, "Open DMs")

    // ---- Instagram ----

    @Test
    fun `instagram blocks home feed, reels tab and explore`() {
        assertBlocked("home_feed", FixtureScreens.igHomeFeed)
        assertBlocked("reels_tab", FixtureScreens.igReelsTab)
        assertBlocked("explore", FixtureScreens.igExplore)
    }

    @Test
    fun `instagram blocks the home feed right after launch, before any tab is selected`() {
        assertBlocked("home_feed", FixtureScreens.igColdStartFeed)
    }

    @Test
    fun `instagram profile tab is not mistaken for the cold-start feed`() {
        assertAllowed(
            FixtureScreens.igColdStartFeed.copy(selectedViewIds = setOf("com.instagram.android:id/profile_tab")),
        )
    }

    @Test
    fun `instagram blocks redirect to the DM inbox, then fall back to back`() {
        assertEquals(listOf(toInbox, BlockAction.Back), (decide(FixtureScreens.igHomeFeed) as Decision.Block).actions)
    }

    @Test
    fun `instagram allows DM inbox, threads and notifications`() {
        assertAllowed(FixtureScreens.igDmInbox)
        assertAllowed(FixtureScreens.igDmThread)
        assertAllowed(FixtureScreens.igNotifications)
        assertAllowed(FixtureScreens.igNotificationsOverReels)
    }

    @Test
    fun `off-screen reels pager behind the home and inbox tabs is not mistaken for the reel viewer`() {
        assertEquals("home_feed", (decide(FixtureScreens.igHomeFeed) as Decision.Block).surfaceId)
        assertAllowed(FixtureScreens.igDmInbox)
    }

    @Test
    fun `explore grid kept off-screen behind home is not mistaken for explore`() {
        assertEquals("home_feed", (decide(FixtureScreens.igHomeWithExploreOffscreen) as Decision.Block).surfaceId)
    }

    @Test
    fun `search tab before the grid loads is allowed until the grid appears`() {
        assertAllowed(FixtureScreens.igExploreLoading)
    }

    // ---- Reel from a chat ----

    @Test
    fun `reel opened from a DM thread plays, swiping to the next reel goes back`() {
        val tracker = ReelFromChatTracker()
        assertEquals(Decision.Allow, decide(FixtureScreens.igDmThread, tracker = tracker))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelViewer(1), tracker = tracker))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelViewer(1, "android.view.View"), tracker = tracker))
        val swiped = decide(FixtureScreens.igReelViewer(2, "android.view.View"), tracker = tracker)
        assertEquals(Decision.Block("reels_tab", listOf(toInbox, BlockAction.Back)), swiped)
    }

    @Test
    fun `reel viewer reached from the DM inbox rather than a thread is blocked`() {
        val tracker = ReelFromChatTracker()
        decide(FixtureScreens.igDmInbox, tracker = tracker)
        assertTrue(decide(FixtureScreens.igReelViewer(1), tracker = tracker) is Decision.Block)
    }

    @Test
    fun `reels from chats set to block blocks even the first reel`() {
        val tracker = ReelFromChatTracker()
        val settings = defaults.copy(reelsFromChats = ReelsFromChats.BLOCK)
        decide(FixtureScreens.igDmThread, settings, tracker)
        assertTrue(decide(FixtureScreens.igReelViewer(1), settings, tracker) is Decision.Block)
    }

    // ---- Facebook ----

    @Test
    fun `facebook blocks the feed by opening messenger`() {
        val decision = decide(FixtureScreens.fbFeed) as Decision.Block
        assertEquals("news_feed", decision.surfaceId)
        assertEquals(
            listOf(BlockAction.Launch("com.facebook.orca", "Open Messenger"), BlockAction.Back),
            decision.actions,
        )
    }

    @Test
    fun `facebook blocks the reels tab and a reel opened from the feed`() {
        assertBlocked("reels", FixtureScreens.fbReelsTab)
        assertBlocked("reels", FixtureScreens.fbReelDetails)
    }

    @Test
    fun `facebook allows notifications and the menu`() {
        assertAllowed(FixtureScreens.fbNotifications)
        assertAllowed(FixtureScreens.fbMenu)
    }

    private fun openFromMessenger(tracker: ReelFromChatTracker) =
        BlockDecider.onWindowOpened(FixtureScreens.FB, FixtureScreens.FB_IMMERSIVE, rules, AppSession(reels = tracker))

    private val toMessenger = listOf(BlockAction.Launch("com.facebook.orca", "Open Messenger"), BlockAction.Back)

    @Test
    fun `facebook reel opened from messenger plays, swiping on opens messenger`() {
        val tracker = ReelFromChatTracker()
        decide(FixtureScreens.fbFeed, tracker = tracker)
        openFromMessenger(tracker)
        assertEquals(Decision.Allow, decide(FixtureScreens.fbReelFromMessenger(1), tracker = tracker))
        assertEquals(Decision.Allow, decide(FixtureScreens.fbReelFromMessenger(1), tracker = tracker))
        val swiped = decide(FixtureScreens.fbReelFromMessenger(2), tracker = tracker)
        assertEquals(Decision.Block("reels", toMessenger), swiped)
        assertTrue(decide(FixtureScreens.fbFeedAfterMessengerReel, tracker = tracker) is Decision.Block)
    }

    @Test
    fun `a second reel from messenger plays too`() {
        val tracker = ReelFromChatTracker()
        openFromMessenger(tracker)
        decide(FixtureScreens.fbReelFromMessenger(1), tracker = tracker)
        openFromMessenger(tracker)
        assertEquals(Decision.Allow, decide(FixtureScreens.fbReelFromMessenger(2), tracker = tracker))
    }

    @Test
    fun `facebook reel viewer without the messenger window opening is blocked`() {
        assertEquals(Decision.Block("reels", toMessenger), decide(FixtureScreens.fbReelFromMessenger(1)))
    }

    @Test
    fun `facebook reels opened inside facebook are still blocked`() {
        val tracker = ReelFromChatTracker()
        openFromMessenger(tracker)
        assertBlocked("reels", FixtureScreens.fbReelDetails)
    }

    // ---- YouTube ----

    @Test
    fun `youtube closes shorts and home`() {
        assertEquals(Decision.Block("shorts", listOf(BlockAction.Home)), decide(FixtureScreens.ytShorts))
        assertEquals(Decision.Block("home_feed", listOf(BlockAction.Home)), decide(FixtureScreens.ytHome))
    }

    // ---- YouTube: one video per visit ----

    private val nextVideo = Decision.Block("next_videos", listOf(BlockAction.Home))

    private fun ytDecide(
        screen: ScreenSignature,
        session: AppSession,
        at: Instant,
        settings: Settings = defaults,
    ) = decide(screen, settings = settings, session = session, now = at)

    private fun openLink(session: AppSession) =
        BlockDecider.onWindowOpened(FixtureScreens.YT, FixtureScreens.YT_MAIN_ACTIVITY, rules, session)

    @Test
    fun `a linked video plays, scrolling is fine, a related video is blocked`() {
        val session = AppSession()
        openLink(session)
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchPage(), session, t0))
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchPage(), session, seconds(20)))
        assertEquals(nextVideo, ytDecide(FixtureScreens.ytWatchLoading(2), session, seconds(30)))
    }

    @Test
    fun `a video picked from search plays, then a related one is blocked`() {
        val session = AppSession()
        ytDecide(FixtureScreens.ytSearch, session, t0)
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(1), session, seconds(1)))
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchPage(), session, seconds(5)))
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(1), session, seconds(6)))
        assertEquals(nextVideo, ytDecide(FixtureScreens.ytWatchLoading(3), session, seconds(60)))
    }

    @Test
    fun `back to search and another result starts a new visit`() {
        val session = AppSession()
        ytDecide(FixtureScreens.ytWatchLoading(1), session, t0)
        ytDecide(FixtureScreens.ytSearch, session, seconds(30))
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(2), session, seconds(31)))
    }

    @Test
    fun `a title that shows up just after the page opens belongs to the first video`() {
        val session = AppSession()
        ytDecide(FixtureScreens.ytWatchPage(), session, t0)
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(1), session, seconds(1)))
        assertEquals(nextVideo, ytDecide(FixtureScreens.ytWatchLoading(2), session, seconds(40)))
    }

    @Test
    fun `a second link while youtube is in the background plays`() {
        val session = AppSession()
        ytDecide(FixtureScreens.ytWatchLoading(1), session, t0)
        openLink(session)
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchPage(), session, seconds(300)))
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(5), session, seconds(301)))
    }

    @Test
    fun `next videos switched off in settings are allowed`() {
        val settings = defaults.copy(apps = mapOf("youtube" to AppBlocking(disabledSurfaces = setOf("next_videos"))))
        val session = AppSession()
        ytDecide(FixtureScreens.ytWatchLoading(1), session, t0, settings)
        assertEquals(Decision.Allow, ytDecide(FixtureScreens.ytWatchLoading(2), session, seconds(60), settings))
    }

    @Test
    fun `youtube allows search, subscriptions and a watched video`() {
        assertAllowed(FixtureScreens.ytSearch)
        assertAllowed(FixtureScreens.ytSearchBox)
        assertAllowed(FixtureScreens.ytSubscriptions)
        assertAllowed(FixtureScreens.ytWatch)
    }

    // ---- Reddit: thread only ----

    @Test
    fun `reddit blocks every feed and closes the app`() {
        listOf(
            "home_feed" to FixtureScreens.redditHome,
            "popular" to FixtureScreens.redditPopular,
            "subreddit_feed" to FixtureScreens.redditSubreddit,
            "search_results" to FixtureScreens.redditSearchResults,
            "video_feed" to FixtureScreens.redditVideoFeed,
        ).forEach { (surface, screen) ->
            assertEquals(Decision.Block(surface, listOf(BlockAction.Home)), decide(screen))
        }
    }

    @Test
    fun `reddit thread from a google link is allowed, back into a feed exits`() {
        val tracker = ReelFromChatTracker()
        assertEquals(Decision.Allow, decide(FixtureScreens.redditThread, tracker = tracker))
        assertTrue(decide(FixtureScreens.redditHomeAfterThread, tracker = tracker) is Decision.Block)
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

    @Test
    fun `reel viewer is allowed when the user allows reels in instagram`() {
        val settings = defaults.copy(apps = mapOf("instagram" to AppBlocking(disabledSurfaces = setOf("reels_tab"))))
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelViewer(1), settings))
    }

    // ---- Fail open (synthetic rules, independent of the shipped file) ----

    @Test
    fun `unrecognised screens are allowed`() {
        assertAllowed(ScreenSignature(IG, viewIds = setOf("com.instagram.android:id/something_new")))
        assertAllowed(ScreenSignature(FixtureScreens.FB))
    }

    @Test
    fun `apps without rules are allowed`() {
        assertAllowed(ScreenSignature("com.facebook.orca", contentDescriptions = setOf("Reels, tab 2 of 5")))
    }

    @Test
    fun `placeholder rules never block`() {
        assertEquals(Decision.Allow, decide(FixtureScreens.igReelsTab, rules = rules.copy(placeholder = true)))
    }

    @Test
    fun `allowed screens win even when a blocked surface also matches`() {
        val overlap =
            FixtureScreens.igHomeFeed.copy(
                viewIds =
                    FixtureScreens.igHomeFeed.viewIds + "com.instagram.android:id/thread_fragment_container",
            )
        assertAllowed(overlap)
    }

    @Test
    fun `an empty match never matches anything`() {
        val catchAll =
            Rules(1, apps = listOf(AppRules("instagram", IG, surfaces = listOf(SurfaceRule("home_feed", Match())))))
        assertEquals(Decision.Allow, decide(ScreenSignature(IG), rules = catchAll))
    }
}
