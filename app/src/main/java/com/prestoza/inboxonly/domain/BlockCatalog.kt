// BlockCatalog: the fixed list of apps and blockable surfaces the user can toggle in settings.
// These are *our* stable IDs, linking settings to rules.json entries (via "settingKey"). They are not
// selectors: the view IDs that actually detect each surface live only in assets/rules.json.
package com.prestoza.inboxonly.domain

/** A screen type inside an app that can be blocked, e.g. Instagram's Reels tab. */
data class Surface(
    val id: String,
    val label: String,
)

/** An app the blocker knows about, with its blockable surfaces. Chats/DMs are never listed: always allowed. */
data class TargetApp(
    val id: String,
    val label: String,
    val surfaces: List<Surface>,
)

/** The apps and surfaces from the spec's blocking table (§3.4). */
object BlockCatalog {
    val apps: List<TargetApp> =
        listOf(
            TargetApp(
                id = "instagram",
                label = "Instagram",
                surfaces =
                    listOf(
                        Surface("reels_tab", "Reels tab"),
                        Surface("explore", "Explore"),
                        Surface("home_feed", "Home feed"),
                    ),
            ),
            TargetApp(
                id = "facebook",
                label = "Facebook",
                surfaces =
                    listOf(
                        Surface("reels", "Reels"),
                        Surface("video", "Video / Watch"),
                        Surface("news_feed", "News feed"),
                    ),
            ),
            TargetApp(
                id = "youtube",
                label = "YouTube",
                surfaces =
                    listOf(
                        Surface("shorts", "Shorts"),
                        Surface("home_feed", "Home feed"),
                    ),
            ),
            TargetApp(
                id = "reddit",
                label = "Reddit",
                surfaces =
                    listOf(
                        Surface("home_feed", "Home feed"),
                        Surface("popular", "Popular"),
                        Surface("subreddit_feed", "Subreddit feeds"),
                        Surface("search_results", "Search results"),
                        Surface("video_feed", "Full-screen video feed"),
                    ),
            ),
        )

    /** Looks up an app by its ID, or null if unknown. */
    fun app(id: String): TargetApp? = apps.firstOrNull { it.id == id }
}
