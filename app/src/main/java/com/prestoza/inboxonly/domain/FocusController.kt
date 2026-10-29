// FocusController: every Focus state transition lives here: tile taps, timers expiring, schedule
// edges, breaks and strict mode. It's pure: give it the stored state, settings, the current time and
// zone, and it returns the new state plus when the next alarm should fire. Android code only persists
// the result and sets the alarm.
package com.prestoza.inboxonly.domain

import java.time.Instant
import java.time.ZoneId

/** Feedback for the user after a tile tap that didn't do what they might expect. */
sealed interface TapFeedback {
    /** Strict mode refused to start a break; Focus stays on until [until]. */
    data class StrictModeLocked(
        val until: Instant,
    ) : TapFeedback
}

/**
 * The result of a transition.
 *
 * @property state the state to persist.
 * @property feedback something to show the user (a toast), if any.
 * @property nextAlarm when to wake up and call [FocusController.reconcile] again, or null if nothing is pending.
 */
data class Transition(
    val state: FocusState,
    val feedback: TapFeedback? = null,
    val nextAlarm: Instant?,
)

/**
 * Pure Focus state machine.
 *
 * Invariant: while a schedule window is active, the state is always [FocusState.Scheduled] or
 * [FocusState.Break]. Manual Focus that overlaps a window is absorbed by it (see docs/DECISIONS.md).
 */
object FocusController {
    /**
     * Brings a stored state up to date with the clock and the schedule.
     *
     * Called on alarms, boot, time/zone changes and settings changes, and before every decision.
     * Because it only depends on absolute times, it's correct even when an alarm fires late or never
     * fires at all (e.g. the phone was off).
     */
    fun reconcile(
        stored: FocusState,
        settings: Settings,
        now: Instant,
        zone: ZoneId,
    ): FocusState {
        val window = ScheduleCalculator.activeWindow(settings.schedule, now, zone)
        return when (stored) {
            FocusState.Off -> {
                if (window != null) FocusState.Scheduled(window.end) else FocusState.Off
            }

            is FocusState.Manual -> {
                when {
                    window != null -> FocusState.Scheduled(window.end)
                    stored.until != null && now >= stored.until -> FocusState.Off
                    else -> stored
                }
            }

            // Re-derive the end from the window so edits to the schedule take effect immediately.
            is FocusState.Scheduled -> {
                if (window != null) FocusState.Scheduled(window.end) else FocusState.Off
            }

            is FocusState.Break -> {
                when {
                    window == null -> FocusState.Off
                    now >= stored.resumeAt -> FocusState.Scheduled(window.end)
                    else -> FocusState.Break(window.end, stored.resumeAt)
                }
            }
        }
    }

    /** [reconcile] packaged as a [Transition], for alarm, boot and settings-change handlers. */
    fun refresh(
        stored: FocusState,
        settings: Settings,
        now: Instant,
        zone: ZoneId,
    ): Transition {
        val state = reconcile(stored, settings, now, zone)
        return Transition(state, nextAlarm = nextAlarm(state, settings, now, zone))
    }

    /**
     * Handles a tap on the Quick Settings tile.
     *
     * - Off → Manual for the configured tile duration.
     * - Manual → Off.
     * - Scheduled → a break, unless strict mode is on (then nothing changes and we explain why).
     * - Break → back to Scheduled (ends the break early).
     */
    fun onTileTap(
        stored: FocusState,
        settings: Settings,
        now: Instant,
        zone: ZoneId,
    ): Transition {
        var feedback: TapFeedback? = null
        val next =
            when (val current = reconcile(stored, settings, now, zone)) {
                FocusState.Off -> {
                    FocusState.Manual(settings.tileDuration.duration?.let { now.plus(it) })
                }

                is FocusState.Manual -> {
                    FocusState.Off
                }

                is FocusState.Scheduled -> {
                    if (settings.strictMode) {
                        feedback = TapFeedback.StrictModeLocked(current.until)
                        current
                    } else {
                        FocusState.Break(current.until, now.plus(settings.breakLength.duration))
                    }
                }

                is FocusState.Break -> {
                    FocusState.Scheduled(current.until)
                }
            }
        return Transition(next, feedback, nextAlarm(next, settings, now, zone))
    }

    /**
     * The earliest future moment at which [state] could change on its own: a timer ending, a break
     * ending, or the next schedule window starting. Only one alarm is ever needed; when it fires we
     * reconcile and ask again.
     */
    fun nextAlarm(
        state: FocusState,
        settings: Settings,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        val stateEdges =
            when (state) {
                FocusState.Off -> listOf()
                is FocusState.Manual -> listOfNotNull(state.until)
                is FocusState.Scheduled -> listOf(state.until)
                is FocusState.Break -> listOf(state.resumeAt, state.until)
            }
        val nextWindowStart = ScheduleCalculator.nextStart(settings.schedule, now, zone)
        return (stateEdges + listOfNotNull(nextWindowStart)).filter { it > now }.minOrNull()
    }
}
