// ReelFromChatTracker: the small state machine behind "allow one Reel opened from a chat". A friend
// sends a Reel in a DM; tapping it should play that Reel, but swiping on to the next one should not
// turn into a scroll session. Pure Kotlin, one instance per app, held in memory only.
package com.prestoza.inboxonly.domain

/**
 * Tracks whether the Reel currently on screen was opened from a chat.
 *
 * States:
 * - **Idle**: no chat context. Any Reel is treated as a normal Reel.
 * - **InChat**: the last recognised screen was a chat (one of the reel viewer's `openedFrom` screens).
 * - **WatchingAllowedReel**: a Reel opened from a chat is playing; remembers its identity.
 *
 * Screens that match no rule don't change the state: apps show brief transitional screens, and those
 * mustn't break the chat → Reel link. Recognised non-chat screens (inbox, feeds) reset to Idle.
 */
class ReelFromChatTracker {
    private sealed interface State {
        data object Idle : State

        data object InChat : State

        data class WatchingAllowedReel(
            val identity: Int?,
        ) : State
    }

    private var state: State = State.Idle

    /** A recognised allowed screen appeared. [isChat] is true if it counts as a chat for this rule. */
    fun onAllowedScreen(isChat: Boolean) {
        state = if (isChat) State.InChat else State.Idle
    }

    /** A recognised blockable (non-Reel-viewer) screen appeared. */
    fun onOtherRecognisedScreen() {
        state = State.Idle
    }

    /**
     * The Reel viewer is showing. Returns true if it should be allowed.
     *
     * @param identity the current Reel's opaque identity, or null if the rules can't tell Reels apart.
     *   With no identity we can't notice a swipe, so the opened Reel stays allowed until the viewer is left.
     * @param allowOneFromChat the user's "Reels from chats" setting.
     */
    fun onReelViewer(
        identity: Int?,
        allowOneFromChat: Boolean,
    ): Boolean {
        val current = state
        val allowed =
            when (current) {
                State.Idle -> {
                    false
                }

                State.InChat -> {
                    allowOneFromChat
                }

                is State.WatchingAllowedReel -> {
                    identity == null || current.identity == null || identity == current.identity
                }
            }
        state =
            when {
                allowed && current is State.WatchingAllowedReel -> current
                allowed -> State.WatchingAllowedReel(identity)
                else -> State.Idle
            }
        return allowed
    }
}
