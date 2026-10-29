// Settings: the immutable model of everything the user can configure on the settings screen.
// Pure Kotlin so the domain layer (FocusController, BlockDecider) can be unit tested without Android.
// The data layer persists it; this file only defines shape and defaults.
package com.prestoza.inboxonly.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** How long Focus lasts when turned on manually from the tile. `null` duration means "until turned off". */
enum class TileDuration(
    val duration: Duration?,
) {
    THIRTY_MINUTES(Duration.ofMinutes(30)),
    ONE_HOUR(Duration.ofHours(1)),
    TWO_HOURS(Duration.ofHours(2)),
    UNTIL_OFF(null),
}

/** Length of a break taken by tapping the tile off during a scheduled window. */
enum class BreakLength(
    val duration: Duration,
) {
    FIVE_MINUTES(Duration.ofMinutes(5)),
    TEN_MINUTES(Duration.ofMinutes(10)),
    FIFTEEN_MINUTES(Duration.ofMinutes(15)),
}

/** What happens when a Reel is opened from a DM thread. */
enum class ReelsFromChats {
    /** Allow that one Reel; swiping to another sends the user back to the chat. */
    ALLOW_ONE,

    /** Treat it like any other Reel. */
    BLOCK,
}

/**
 * One recurring Focus window, e.g. 08:00–18:00 Mon–Fri.
 *
 * [days] are the days the window *starts* on, so a 22:00–06:00 window on Friday runs into Saturday
 * morning even if Saturday isn't selected. A window whose [start] equals its [end] is invalid and is
 * treated as "no window".
 */
data class Schedule(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime = LocalTime.of(18, 0),
    val days: Set<DayOfWeek> =
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
) {
    /** True when this schedule can produce at least one window. */
    val isUsable: Boolean get() = enabled && days.isNotEmpty() && start != end
}

/**
 * Per-app blocking toggles. Everything defaults to on, so we only store what the user switched off.
 *
 * @property appEnabled master switch for the app.
 * @property disabledSurfaces surface IDs (see [BlockCatalog]) the user chose to allow.
 */
data class AppBlocking(
    val appEnabled: Boolean = true,
    val disabledSurfaces: Set<String> = emptySet(),
)

/** Everything the user can configure. Defaults match the spec: blocking on, schedule off, strict off. */
data class Settings(
    val apps: Map<String, AppBlocking> = emptyMap(),
    val schedule: Schedule = Schedule(),
    val tileDuration: TileDuration = TileDuration.ONE_HOUR,
    val breakLength: BreakLength = BreakLength.FIVE_MINUTES,
    val reelsFromChats: ReelsFromChats = ReelsFromChats.ALLOW_ONE,
    val strictMode: Boolean = false,
) {
    /** Blocking settings for [appId], falling back to the all-on default. */
    fun blockingFor(appId: String): AppBlocking = apps[appId] ?: AppBlocking()

    /** True when [surfaceId] of [appId] should be blocked while Focus is on. */
    fun isSurfaceBlocked(
        appId: String,
        surfaceId: String,
    ): Boolean {
        val app = blockingFor(appId)
        return app.appEnabled && surfaceId !in app.disabledSurfaces
    }
}
