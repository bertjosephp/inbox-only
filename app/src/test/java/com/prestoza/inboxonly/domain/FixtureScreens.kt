package com.prestoza.inboxonly.domain

import com.prestoza.inboxonly.data.rules.RulesRepository
import java.io.File

/**
 * Real screen signatures captured on a Galaxy S25+ (2026-10-29) with scripts/inspect.sh, reduced to the
 * view IDs and content descriptions that the shipped rules.json references, exactly as the service sees
 * them. `#N` is the screen's position in the capture (`c1` = first session, `c2` = second).
 *
 * Reel identities are stand-in numbers: the real ones are hashes of on-screen labels and are never
 * logged, so a capture can't contain them. Different numbers mean different Reels.
 */
object FixtureScreens {
    const val IG = "com.instagram.android"
    const val FB = "com.facebook.katana"
    const val YT = "com.google.android.youtube"
    const val REDDIT = "com.reddit.frontpage"

    /** The rules that ship in the APK. */
    val rules: Rules by lazy {
        val text = File("src/main/assets/rules.json").readText()
        requireNotNull(RulesRepository.parse(text)) { "assets/rules.json failed to parse" }
    }

    private fun ig(id: String) = "com.instagram.android:id/$id"

    private fun yt(id: String) = "com.google.android.youtube:id/$id"

    // Instagram's main activity keeps every tab's content in the tree, including an off-screen Reels pager
    // and the Home feed's logo.
    private val igMainTabs =
        setOf(ig("feed_tab"), ig("clips_tab"), ig("direct_tab"), ig("search_tab"), ig("profile_tab"))
    private val igOffscreenReels = setOf(ig("clips_viewer_view_pager"), ig("clips_author_username"))
    private const val IG_HOME_LOGO = "Instagram Home Feed"

    private fun igMain(
        selectedTab: String?,
        extra: Set<String> = emptySet(),
    ) = ScreenSignature(
        IG,
        windowClass = "com.instagram.mainactivity.InstagramMainActivity",
        viewIds = igMainTabs + extra,
        selectedViewIds = setOfNotNull(selectedTab?.let(::ig)),
        contentDescriptions = setOf(IG_HOME_LOGO),
    )

    /** c1 #1–#4, c2 #1–#2: Home feed right after launch, before any tab has been tapped (none is selected). */
    val igColdStartFeed = igMain(selectedTab = null)

    /** c1 #7: Home feed. */
    val igHomeFeed = igMain("feed_tab", igOffscreenReels)

    /** c1 #5: Reels tab. */
    val igReelsTab = igMain("clips_tab", igOffscreenReels)

    /** c1 #6: DM inbox (now a bottom tab). */
    val igDmInbox = igMain("direct_tab", igOffscreenReels)

    /** c2 #4: Explore grid. */
    val igExplore = igMain("search_tab", setOf(ig("grid_card_layout_container")))

    /** c2 #3: Search tab before the grid has loaded. */
    val igExploreLoading = igMain("search_tab")

    /** c2 #21: Home feed with the Explore grid kept off-screen in the tree. */
    val igHomeWithExploreOffscreen = igMain("feed_tab", setOf(ig("grid_card_layout_container")))

    /** c1 #14: a DM thread. */
    val igDmThread =
        ScreenSignature(IG, "com.instagram.modal.ModalActivity", viewIds = setOf(ig("thread_fragment_container")))

    /** c2 #22: Notifications (the tab bar is hidden). */
    val igNotifications =
        ScreenSignature(
            IG,
            "com.instagram.mainactivity.InstagramMainActivity",
            viewIds = setOf(ig("activity_feed_root"), ig("grid_card_layout_container")),
        )

    /** c1 #8: Notifications opening over the Reels pager. */
    val igNotificationsOverReels =
        ScreenSignature(
            IG,
            "com.instagram.mainactivity.InstagramMainActivity",
            viewIds = igOffscreenReels + ig("activity_feed_root"),
        )

    /** c1 #16 (opened from the DM thread) and #17–#20 (after swiping): the full-screen Reel viewer, no tab bar. */
    fun igReelViewer(
        identity: Int?,
        windowClass: String = "com.instagram.modal.TransparentModalActivity",
    ) = ScreenSignature(IG, windowClass, viewIds = igOffscreenReels, reelIdentity = identity)

    private fun fb(
        descriptions: Set<String>,
        selected: Set<String> = emptySet(),
    ) = ScreenSignature(
        FB,
        "com.facebook.katana.activity.FbMainTabActivity",
        contentDescriptions = descriptions,
        selectedContentDescriptions = selected,
    )

    private const val FB_HOME = "Home, tab 1 of 5"
    private const val FB_REELS = "Reels, tab 2 of 5"
    private const val FB_NOTIFICATIONS = "Notifications, tab 4 of 5"

    /** c1 #22: News feed. */
    val fbFeed = fb(setOf(FB_HOME, FB_REELS, FB_NOTIFICATIONS), setOf(FB_HOME))

    /** c1 #27: Reels tab. */
    val fbReelsTab = fb(setOf(FB_HOME, FB_REELS), setOf(FB_REELS))

    /** c1 #50: a Reel opened from the feed. */
    val fbReelDetails = fb(setOf("Reel details"))

    /** c1 #57: Notifications tab. */
    val fbNotifications = fb(setOf(FB_HOME, FB_REELS, FB_NOTIFICATIONS), setOf(FB_NOTIFICATIONS))

    /** c1 #30: the ☰ menu (covers the tab bar). */
    val fbMenu = fb(emptySet())

    /** c1 #62: Home. */
    val ytHome =
        ScreenSignature(
            YT,
            "android.view.ViewGroup",
            contentDescriptions = setOf("Home"),
            selectedContentDescriptions = setOf("Home"),
        )

    /** c1 #59: Shorts player. */
    val ytShorts =
        ScreenSignature(
            YT,
            "com.google.android.apps.youtube.app.watchwhile.MainActivity",
            viewIds = setOf(yt("reel_recycler")),
            contentDescriptions = setOf("Home"),
        )

    /** c1 #70: search results. */
    val ytSearch =
        ScreenSignature(
            YT,
            "android.view.ViewGroup",
            viewIds = setOf(yt("search_query")),
            contentDescriptions = setOf("Home"),
        )

    /** c1 #67: the search box with suggestions (the tab bar is hidden). */
    val ytSearchBox = ScreenSignature(YT, "android.view.ViewGroup", viewIds = setOf(yt("search_edit_text")))

    /** c1 #72: Subscriptions. */
    val ytSubscriptions =
        ScreenSignature(
            YT,
            "android.view.ViewGroup",
            contentDescriptions = setOf("Home", "Subscriptions"),
            selectedContentDescriptions = setOf("Subscriptions"),
        )

    /** c1 #106: a video's watch page. */
    val ytWatch = ScreenSignature(YT, "android.view.ViewGroup", viewIds = setOf(yt("watch_panel")))

    private fun reddit(
        tag: String,
        windowClass: String = "com.reddit.launch.main.MainActivity",
    ) = ScreenSignature(REDDIT, windowClass, viewIds = setOf(tag))

    /** c1 #110: Home (opened from the launcher). */
    val redditHome = reddit("home_screen_surface")

    /** c1 #116: Popular. */
    val redditPopular = reddit("popular_screen_surface")

    /** c1 #124: a subreddit. */
    val redditSubreddit = reddit("subreddit_screen_surface", "android.view.ViewGroup")

    /** c1 #131: search results. */
    val redditSearchResults = reddit("search_screen_surface", "android.view.View")

    /** c1 #193: full-screen video feed. */
    val redditVideoFeed = reddit("fbp_screen", "com.reddit.fullbleedplayer.common.FbpActivity")

    /** c1 #211: a thread opened from a Google search result. */
    val redditThread = reddit("post_detail_scaffold")

    /** c1 #208: Home after pressing back out of a thread. */
    val redditHomeAfterThread = reddit("home_screen_surface")
}
