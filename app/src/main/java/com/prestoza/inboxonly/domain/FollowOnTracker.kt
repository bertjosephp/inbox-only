// FollowOnTracker: the state behind "one video per visit" on YouTube's watch page. A video opened from a
// link, Search or Subscriptions plays; picking another one from "up next" (or autoplay moving on) while
// staying on the watch page doesn't. Pure Kotlin, one instance per app, held in memory only.
package com.prestoza.inboxonly.domain

import java.time.Duration
import java.time.Instant

/**
 * Remembers the first item of the current visit to a [FollowOnRule] page.
 *
 * YouTube shows the identifying title only while a video loads, and not at all for one opened from a
 * link. So a visit can start with no identity; the first identity seen within [adoptWithin] of the
 * visit's start still belongs to the first video. After that, any new identity is a different video.
 */
class FollowOnTracker(
    private val adoptWithin: Duration = Duration.ofSeconds(2),
) {
    private var visitStart: Instant? = null
    private var firstIdentity: Int? = null

    /** A matching screen appeared. Returns true if it shows a different item than the visit's first. */
    fun onPage(
        identity: Int?,
        now: Instant,
    ): Boolean {
        val start = visitStart
        if (start == null) {
            visitStart = now
            firstIdentity = identity
            return false
        }
        if (identity == null || identity == firstIdentity) return false
        if (firstIdentity == null && Duration.between(start, now) <= adoptWithin) {
            firstIdentity = identity
            return false
        }
        return true
    }

    /** Any other screen appeared, or the app was opened afresh: the next matching screen starts a visit. */
    fun endVisit() {
        visitStart = null
        firstIdentity = null
    }
}
