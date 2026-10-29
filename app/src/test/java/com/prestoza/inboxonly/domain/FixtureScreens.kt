package com.prestoza.inboxonly.domain

import com.prestoza.inboxonly.data.rules.RulesRepository
import java.io.File

/**
 * Hand-written screen signatures modelling each screen type, matched against fixture_rules.json.
 * Every identifier is invented. These get replaced by real captured signatures at Final Checkpoint A.
 */
object FixtureScreens {
    const val IG = "com.instagram.android"
    const val FB = "com.facebook.katana"
    const val YT = "com.google.android.youtube"
    const val REDDIT = "com.reddit.frontpage"

    val rules: Rules by lazy {
        val text = File("src/test/resources/fixture_rules.json").readText()
        requireNotNull(RulesRepository.parse(text)) { "fixture_rules.json failed to parse" }
    }

    private fun ig(
        ids: Set<String>,
        selected: Set<String> = emptySet(),
        reel: Int? = null,
    ) = ScreenSignature(IG, viewIds = ids + selected, selectedViewIds = selected, reelIdentity = reel)

    private val igNav =
        setOf("fixture:id/bottom_nav", "fixture:id/tab_home", "fixture:id/tab_search", "fixture:id/tab_reels")

    val igHomeFeed = ig(igNav, selected = setOf("fixture:id/tab_home"))
    val igReelsTab = ig(igNav + "fixture:id/reel_viewer", selected = setOf("fixture:id/tab_reels"), reel = 101)
    val igExplore = ig(igNav, selected = setOf("fixture:id/tab_search"))
    val igSearchResults = ig(igNav + "fixture:id/search_results", selected = setOf("fixture:id/tab_search"))
    val igDmInbox = ig(setOf("fixture:id/inbox_list"))
    val igDmThread = ig(setOf("fixture:id/thread_composer"))
    val igNotifications = ig(setOf("fixture:id/activity_feed"))

    fun igReelFromChat(identity: Int?) = ig(setOf("fixture:id/reel_viewer", "fixture:id/reel_author"), reel = identity)

    private fun withDesc(
        pkg: String,
        selectedDesc: String,
    ) = ScreenSignature(
        pkg,
        contentDescriptions = setOf(selectedDesc),
        selectedContentDescriptions = setOf(selectedDesc),
    )

    val fbFeed = withDesc(FB, "fixture-desc Home")
    val fbVideo = withDesc(FB, "fixture-desc Video")
    val fbReels = ScreenSignature(FB, viewIds = setOf("fixture:id/fb_reels_viewer"))
    val fbNotifications = withDesc(FB, "fixture-desc Notifications")

    val ytHome = withDesc(YT, "fixture-desc Home")
    val ytShorts = ScreenSignature(YT, viewIds = setOf("fixture:id/shorts_player"))
    val ytSearch = ScreenSignature(YT, viewIds = setOf("fixture:id/search_box"))
    val ytSubscriptions = withDesc(YT, "fixture-desc Subscriptions")
    val ytWatch = ScreenSignature(YT, viewIds = setOf("fixture:id/watch_player"))

    val redditHome = withDesc(REDDIT, "fixture-desc Home")
    val redditPopular = withDesc(REDDIT, "fixture-desc Popular")
    val redditSubreddit = ScreenSignature(REDDIT, viewIds = setOf("fixture:id/subreddit_header"))
    val redditSearchResults = ScreenSignature(REDDIT, viewIds = setOf("fixture:id/search_results_list"))
    val redditVideoFeed = ScreenSignature(REDDIT, viewIds = setOf("fixture:id/fullscreen_video_pager"))
    val redditThread = ScreenSignature(REDDIT, viewIds = setOf("fixture:id/comments_list"))
}
