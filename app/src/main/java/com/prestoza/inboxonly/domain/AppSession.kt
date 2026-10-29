// AppSession: the in-memory history BlockDecider needs for one app, so a decision can depend on what
// came before (a Reel opened from a chat, the first video of a visit). Never persisted.
package com.prestoza.inboxonly.domain

/** Per-app decision state. One instance per target app, owned by the accessibility service. */
class AppSession(
    val reels: ReelFromChatTracker = ReelFromChatTracker(),
    val followOn: FollowOnTracker = FollowOnTracker(),
)
