// FocusCoordinator: the one place where Focus state changes are applied. It reads the stores, asks
// the pure FocusController what to do, saves the result and registers the next alarm. The tile,
// alarm/boot receivers and settings screen all go through here, so they can never disagree.
package com.prestoza.inboxonly.service

import com.prestoza.inboxonly.data.CounterStore
import com.prestoza.inboxonly.data.FocusStateStore
import com.prestoza.inboxonly.data.SettingsRepository
import com.prestoza.inboxonly.data.TimeSource
import com.prestoza.inboxonly.domain.FocusController
import com.prestoza.inboxonly.domain.FocusState
import com.prestoza.inboxonly.domain.Settings
import com.prestoza.inboxonly.domain.Transition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Applies Focus transitions and keeps the alarm in sync with the stored state. */
class FocusCoordinator(
    private val settingsRepository: SettingsRepository,
    private val focusStateStore: FocusStateStore,
    private val counterStore: CounterStore,
    private val timeSource: TimeSource,
    private val alarmScheduler: AlarmScheduler,
) {
    // A tile tap and an alarm can arrive at the same moment; each read-decide-write must not interleave.
    private val mutex = Mutex()

    /** The stored state brought up to date with the clock. Doesn't save anything. */
    suspend fun currentState(): FocusState =
        FocusController.reconcile(
            focusStateStore.current(),
            settingsRepository.settings.first(),
            timeSource.now(),
            timeSource.zone(),
        )

    /** Handles a Quick Settings tile tap. Returns the transition so the caller can show feedback. */
    suspend fun onTileTap(): Transition =
        apply { stored, settings ->
            FocusController.onTileTap(stored, settings, timeSource.now(), timeSource.zone())
        }

    /** Re-evaluates state after an alarm, reboot, clock or time zone change. */
    suspend fun refresh(): Transition =
        apply { stored, settings ->
            FocusController.refresh(stored, settings, timeSource.now(), timeSource.zone())
        }

    /** Saves new settings and immediately re-evaluates Focus (e.g. the schedule was just switched on). */
    suspend fun updateSettings(transform: (Settings) -> Settings): Transition {
        settingsRepository.update(transform)
        return refresh()
    }

    /** Today's block count. */
    suspend fun blockedToday(): Int = counterStore.countOn(timeSource.today())

    /** Records one block event in today's counter. */
    suspend fun recordBlock() = counterStore.increment(timeSource.today())

    private suspend fun apply(decide: (FocusState, Settings) -> Transition): Transition =
        mutex.withLock {
            val stored = focusStateStore.current()
            val transition = decide(stored, settingsRepository.settings.first())
            if (transition.state != stored) focusStateStore.save(transition.state)
            alarmScheduler.scheduleAt(transition.nextAlarm)
            transition
        }
}
