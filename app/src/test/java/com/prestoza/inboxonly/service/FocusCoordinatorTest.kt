package com.prestoza.inboxonly.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.prestoza.inboxonly.data.CounterStore
import com.prestoza.inboxonly.data.FocusStateStore
import com.prestoza.inboxonly.data.SettingsRepository
import com.prestoza.inboxonly.data.TimeSource
import com.prestoza.inboxonly.domain.FocusState
import com.prestoza.inboxonly.domain.Schedule
import com.prestoza.inboxonly.domain.TapFeedback
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class FocusCoordinatorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("America/Los_Angeles")

    private class FakeClock(
        var now: Instant,
        val zoneId: ZoneId,
    ) : TimeSource {
        override fun now() = now

        override fun zone() = zoneId
    }

    private class RecordingAlarms : AlarmScheduler {
        val scheduled = mutableListOf<Instant?>()

        override fun scheduleAt(at: Instant?) {
            scheduled += at
        }
    }

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private class Harness(
        val coordinator: FocusCoordinator,
        val store: FocusStateStore,
        val clock: FakeClock,
        val alarms: RecordingAlarms,
    )

    private fun TestScope.harness(start: Instant): Harness {
        fun dataStore(name: String) =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "$name.preferences_pb") },
            )
        val store = FocusStateStore(dataStore("focus"))
        val clock = FakeClock(start, zone)
        val alarms = RecordingAlarms()
        val coordinator =
            FocusCoordinator(
                settingsRepository = SettingsRepository(dataStore("settings")),
                focusStateStore = store,
                counterStore = CounterStore(dataStore("counter")),
                timeSource = clock,
                alarmScheduler = alarms,
            )
        return Harness(coordinator, store, clock, alarms)
    }

    @Test
    fun `tile tap persists manual focus and schedules auto-off`() =
        runTest {
            val h = harness(at("2026-10-29T20:00"))
            h.coordinator.onTileTap()
            assertEquals(FocusState.Manual(at("2026-10-29T21:00")), h.store.current())
            assertEquals(listOf<Instant?>(at("2026-10-29T21:00")), h.alarms.scheduled)
        }

    @Test
    fun `alarm at auto-off time turns focus off and clears the alarm`() =
        runTest {
            val h = harness(at("2026-10-29T20:00"))
            h.coordinator.onTileTap()
            h.clock.now = at("2026-10-29T21:00")
            h.coordinator.refresh()
            assertEquals(FocusState.Off, h.store.current())
            assertNull(h.alarms.scheduled.last())
        }

    @Test
    fun `second tap turns focus off`() =
        runTest {
            val h = harness(at("2026-10-29T20:00"))
            h.coordinator.onTileTap()
            h.coordinator.onTileTap()
            assertEquals(FocusState.Off, h.store.current())
        }

    @Test
    fun `enabling the schedule mid-window turns focus on immediately`() =
        runTest {
            val h = harness(at("2026-10-29T12:00")) // Thursday
            h.coordinator.updateSettings {
                it.copy(schedule = Schedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(18, 0)))
            }
            assertEquals(FocusState.Scheduled(at("2026-10-29T18:00")), h.store.current())
            assertEquals(at("2026-10-29T18:00"), h.alarms.scheduled.last())
        }

    @Test
    fun `strict mode tap during a window reports feedback and changes nothing`() =
        runTest {
            val h = harness(at("2026-10-29T12:00"))
            h.coordinator.updateSettings {
                it.copy(
                    schedule = Schedule(enabled = true, days = DayOfWeek.entries.toSet()),
                    strictMode = true,
                )
            }
            val transition = h.coordinator.onTileTap()
            assertEquals(TapFeedback.StrictModeLocked(at("2026-10-29T18:00")), transition.feedback)
            assertEquals(FocusState.Scheduled(at("2026-10-29T18:00")), h.store.current())
        }

    @Test
    fun `current state reconciles without saving`() =
        runTest {
            val h = harness(at("2026-10-29T20:00"))
            h.coordinator.onTileTap()
            h.clock.now = h.clock.now.plus(Duration.ofHours(2))
            assertEquals(FocusState.Off, h.coordinator.currentState())
            assertEquals(FocusState.Manual(at("2026-10-29T21:00")), h.store.current())
        }

    @Test
    fun `blocks are counted per day`() =
        runTest {
            val h = harness(at("2026-10-29T20:00"))
            repeat(2) { h.coordinator.recordBlock() }
            assertEquals(2, h.coordinator.blockedToday())
            h.clock.now = at("2026-10-30T00:00")
            assertEquals(0, h.coordinator.blockedToday())
        }
}
