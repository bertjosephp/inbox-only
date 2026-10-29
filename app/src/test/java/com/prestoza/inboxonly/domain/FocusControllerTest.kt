package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class FocusControllerTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    // 2026-10-29 is a Thursday. The schedule is 08:00–18:00 Mon–Fri.
    private val schedule =
        Schedule(
            enabled = true,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(18, 0),
            days =
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                ),
        )
    private val noSchedule = Settings()
    private val withSchedule = Settings(schedule = schedule)
    private val strict = withSchedule.copy(strictMode = true)

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private val windowEnd = at("2026-10-29T18:00")

    // ---- Tile taps outside a schedule window ----

    @Test
    fun `tap while off starts manual focus for the tile duration`() {
        val now = at("2026-10-29T20:00")
        val t = FocusController.onTileTap(FocusState.Off, noSchedule, now, zone)
        assertEquals(FocusState.Manual(at("2026-10-29T21:00")), t.state)
        assertEquals(at("2026-10-29T21:00"), t.nextAlarm)
        assertNull(t.feedback)
    }

    @Test
    fun `tap while off with until-off duration starts open-ended focus with no alarm`() {
        val settings = noSchedule.copy(tileDuration = TileDuration.UNTIL_OFF)
        val t = FocusController.onTileTap(FocusState.Off, settings, at("2026-10-29T20:00"), zone)
        assertEquals(FocusState.Manual(null), t.state)
        assertNull(t.nextAlarm)
    }

    @Test
    fun `each tile duration is applied`() {
        val now = at("2026-10-29T20:00")
        mapOf(
            TileDuration.THIRTY_MINUTES to Duration.ofMinutes(30),
            TileDuration.ONE_HOUR to Duration.ofHours(1),
            TileDuration.TWO_HOURS to Duration.ofHours(2),
        ).forEach { (setting, length) ->
            val t = FocusController.onTileTap(FocusState.Off, noSchedule.copy(tileDuration = setting), now, zone)
            assertEquals(FocusState.Manual(now.plus(length)), t.state)
        }
    }

    @Test
    fun `tap while manual turns focus off`() {
        val t = FocusController.onTileTap(FocusState.Manual(null), noSchedule, at("2026-10-29T20:00"), zone)
        assertEquals(FocusState.Off, t.state)
        assertNull(t.nextAlarm)
    }

    @Test
    fun `tap after manual focus already expired starts a fresh session instead of turning off`() {
        // The auto-off alarm was late; the user sees "off" in the tile and taps to turn it on.
        val expired = FocusState.Manual(at("2026-10-29T19:00"))
        val t = FocusController.onTileTap(expired, noSchedule, at("2026-10-29T20:00"), zone)
        assertEquals(FocusState.Manual(at("2026-10-29T21:00")), t.state)
    }

    @Test
    fun `manual off-hours tap schedules the earlier of auto-off and next window start`() {
        val settings = withSchedule.copy(tileDuration = TileDuration.TWO_HOURS)
        val t = FocusController.onTileTap(FocusState.Off, settings, at("2026-10-30T07:00"), zone)
        assertEquals(at("2026-10-30T08:00"), t.nextAlarm)
    }

    // ---- Reconcile: timers and schedule edges ----

    @Test
    fun `manual focus turns off once its time has passed`() {
        val stored = FocusState.Manual(at("2026-10-29T21:00"))
        assertEquals(stored, FocusController.reconcile(stored, noSchedule, at("2026-10-29T20:59"), zone))
        assertEquals(FocusState.Off, FocusController.reconcile(stored, noSchedule, at("2026-10-29T21:00"), zone))
    }

    @Test
    fun `until-off manual focus never expires on its own`() {
        val stored = FocusState.Manual(null)
        assertEquals(stored, FocusController.reconcile(stored, noSchedule, at("2027-01-01T00:00"), zone))
    }

    @Test
    fun `window start turns focus on`() {
        val state = FocusController.reconcile(FocusState.Off, withSchedule, at("2026-10-29T08:00"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), state)
    }

    @Test
    fun `window end turns focus off and the next alarm is the next window start`() {
        val t = FocusController.refresh(FocusState.Scheduled(windowEnd), withSchedule, windowEnd, zone)
        assertEquals(FocusState.Off, t.state)
        assertEquals(at("2026-10-30T08:00"), t.nextAlarm)
    }

    @Test
    fun `manual focus running into a window is absorbed by the schedule`() {
        val stored = FocusState.Manual(at("2026-10-29T09:00"))
        val state = FocusController.reconcile(stored, withSchedule, at("2026-10-29T08:00"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), state)
    }

    @Test
    fun `turning on the schedule mid-window turns focus on`() {
        val state = FocusController.reconcile(FocusState.Off, withSchedule, at("2026-10-29T12:00"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), state)
    }

    @Test
    fun `turning off the schedule mid-window turns focus off`() {
        val now = at("2026-10-29T12:00")
        assertEquals(FocusState.Off, FocusController.reconcile(FocusState.Scheduled(windowEnd), noSchedule, now, zone))
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        assertEquals(FocusState.Off, FocusController.reconcile(onBreak, noSchedule, now, zone))
    }

    @Test
    fun `editing the window end updates the scheduled end`() {
        val earlier = withSchedule.copy(schedule = schedule.copy(end = LocalTime.of(17, 0)))
        val state = FocusController.reconcile(FocusState.Scheduled(windowEnd), earlier, at("2026-10-29T12:00"), zone)
        assertEquals(FocusState.Scheduled(at("2026-10-29T17:00")), state)
    }

    // ---- Breaks and strict mode ----

    @Test
    fun `tap during a scheduled window starts a break`() {
        val now = at("2026-10-29T12:00")
        val t = FocusController.onTileTap(FocusState.Scheduled(windowEnd), withSchedule, now, zone)
        assertEquals(FocusState.Break(windowEnd, at("2026-10-29T12:05")), t.state)
        assertEquals(at("2026-10-29T12:05"), t.nextAlarm)
        assertFalse(t.state.isBlocking)
    }

    @Test
    fun `break length setting is applied`() {
        val settings = withSchedule.copy(breakLength = BreakLength.FIFTEEN_MINUTES)
        val t = FocusController.onTileTap(FocusState.Scheduled(windowEnd), settings, at("2026-10-29T12:00"), zone)
        assertEquals(FocusState.Break(windowEnd, at("2026-10-29T12:15")), t.state)
    }

    @Test
    fun `focus resumes automatically when the break ends`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val t = FocusController.refresh(onBreak, withSchedule, at("2026-10-29T12:05"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), t.state)
        assertEquals(windowEnd, t.nextAlarm)
    }

    @Test
    fun `tap during a break ends it early`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val t = FocusController.onTileTap(onBreak, withSchedule, at("2026-10-29T12:02"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), t.state)
    }

    @Test
    fun `break that would outlast the window ends with the window`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T18:03"))
        val t = FocusController.refresh(onBreak, withSchedule, windowEnd, zone)
        assertEquals(FocusState.Off, t.state)
    }

    @Test
    fun `break near window end alarms at the window end first`() {
        val now = at("2026-10-29T17:58")
        val t = FocusController.onTileTap(FocusState.Scheduled(windowEnd), withSchedule, now, zone)
        assertEquals(windowEnd, t.nextAlarm)
    }

    @Test
    fun `strict mode refuses a break and explains until when`() {
        val t = FocusController.onTileTap(FocusState.Scheduled(windowEnd), strict, at("2026-10-29T12:00"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), t.state)
        assertEquals(TapFeedback.StrictModeLocked(windowEnd), t.feedback)
        assertEquals(windowEnd, t.nextAlarm)
    }

    @Test
    fun `strict mode switched on during a break lets the break finish`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        assertEquals(onBreak, FocusController.reconcile(onBreak, strict, at("2026-10-29T12:02"), zone))
        assertEquals(
            FocusState.Scheduled(windowEnd),
            FocusController.reconcile(onBreak, strict, at("2026-10-29T12:05"), zone),
        )
    }

    @Test
    fun `strict mode still allows ending a break early`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val t = FocusController.onTileTap(onBreak, strict, at("2026-10-29T12:02"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), t.state)
        assertNull(t.feedback)
    }

    @Test
    fun `strict mode does not lock manual focus outside a window`() {
        val t = FocusController.onTileTap(FocusState.Manual(null), strict, at("2026-10-29T20:00"), zone)
        assertEquals(FocusState.Off, t.state)
    }

    // ---- Reboot and missed alarms ----

    @Test
    fun `reboot mid-break before it ends keeps the break`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val t = FocusController.refresh(onBreak, withSchedule, at("2026-10-29T12:03"), zone)
        assertEquals(onBreak, t.state)
        assertEquals(at("2026-10-29T12:05"), t.nextAlarm)
    }

    @Test
    fun `reboot mid-break after it should have ended resumes focus`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val state = FocusController.reconcile(onBreak, withSchedule, at("2026-10-29T12:30"), zone)
        assertEquals(FocusState.Scheduled(windowEnd), state)
    }

    @Test
    fun `reboot mid-break after the window ended turns focus off`() {
        val onBreak = FocusState.Break(windowEnd, at("2026-10-29T12:05"))
        val state = FocusController.reconcile(onBreak, withSchedule, at("2026-10-29T19:00"), zone)
        assertEquals(FocusState.Off, state)
    }

    @Test
    fun `phone off across an entire window and into the next one lands in the new window`() {
        val stale = FocusState.Scheduled(windowEnd)
        val state = FocusController.reconcile(stale, withSchedule, at("2026-10-30T09:00"), zone)
        assertEquals(FocusState.Scheduled(at("2026-10-30T18:00")), state)
    }

    // ---- Small invariants ----

    @Test
    fun `only manual and scheduled states block`() {
        assertTrue(FocusState.Manual(null).isBlocking)
        assertTrue(FocusState.Scheduled(windowEnd).isBlocking)
        assertFalse(FocusState.Off.isBlocking)
        assertFalse(FocusState.Break(windowEnd, windowEnd).isBlocking)
    }

    @Test
    fun `off with no schedule needs no alarm`() {
        assertNull(FocusController.nextAlarm(FocusState.Off, noSchedule, at("2026-10-29T12:00"), zone))
    }
}
