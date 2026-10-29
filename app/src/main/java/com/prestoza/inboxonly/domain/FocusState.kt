// FocusState: the one value that says whether blocking is on, why, and until when.
// It's persisted (data/FocusStateStore) so it survives reboots, and every reader resolves it through
// FocusController.reconcile so an expired state is never acted on, even if an alarm fired late.
package com.prestoza.inboxonly.domain

import java.time.Instant

/** Whether Focus is on, and the reason. Times are absolute [Instant]s so time zone changes can't shift them. */
sealed interface FocusState {
    /** True when blocking should happen. A break is a pause, so it doesn't block. */
    val isBlocking: Boolean

    /** Focus is off; the phone behaves normally. */
    data object Off : FocusState {
        override val isBlocking = false
    }

    /** Turned on from the tile. [until] is null for "until turned off". */
    data class Manual(
        val until: Instant?,
    ) : FocusState {
        override val isBlocking = true
    }

    /** On because we're inside the schedule window, which ends at [until]. */
    data class Scheduled(
        val until: Instant,
    ) : FocusState {
        override val isBlocking = true
    }

    /** A paused scheduled window. Focus resumes at [resumeAt]; the window itself ends at [until]. */
    data class Break(
        val until: Instant,
        val resumeAt: Instant,
    ) : FocusState {
        override val isBlocking = false
    }
}
