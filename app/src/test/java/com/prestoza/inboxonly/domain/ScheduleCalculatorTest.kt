package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val weekdays = setOf(MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)

    private fun schedule(
        start: String,
        end: String,
        days: Set<DayOfWeek> = weekdays,
    ) = Schedule(enabled = true, start = LocalTime.parse(start), end = LocalTime.parse(end), days = days)

    private fun at(
        local: String,
        zone: ZoneId = la,
    ): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    // 2026-10-29 is a Thursday.

    @Test
    fun `inside a weekday window returns that window`() {
        val window = ScheduleCalculator.activeWindow(schedule("08:00", "18:00"), at("2026-10-29T12:00"), la)
        assertEquals(ScheduleWindow(at("2026-10-29T08:00"), at("2026-10-29T18:00")), window)
    }

    @Test
    fun `window is half-open - active at start, inactive at end`() {
        val s = schedule("08:00", "18:00")
        assertEquals(at("2026-10-29T08:00"), ScheduleCalculator.activeWindow(s, at("2026-10-29T08:00"), la)?.start)
        assertNull(ScheduleCalculator.activeWindow(s, at("2026-10-29T18:00"), la))
    }

    @Test
    fun `before the window and on unselected days there is no active window`() {
        val s = schedule("08:00", "18:00")
        assertNull(ScheduleCalculator.activeWindow(s, at("2026-10-29T07:59"), la))
        assertNull(ScheduleCalculator.activeWindow(s, at("2026-10-31T12:00"), la)) // Saturday
    }

    @Test
    fun `disabled, dayless and zero-length schedules never produce windows`() {
        val now = at("2026-10-29T12:00")
        val base = schedule("08:00", "18:00")
        listOf(base.copy(enabled = false), base.copy(days = emptySet()), schedule("09:00", "09:00")).forEach {
            assertNull(ScheduleCalculator.activeWindow(it, now, la))
            assertNull(ScheduleCalculator.nextStart(it, now, la))
        }
    }

    @Test
    fun `midnight-crossing window started on a selected day runs into an unselected day`() {
        // Friday 22:00 → Saturday 06:00; Saturday isn't selected but the window started Friday.
        val s = schedule("22:00", "06:00", days = setOf(FRIDAY))
        val window = ScheduleCalculator.activeWindow(s, at("2026-10-31T03:00"), la)
        assertEquals(ScheduleWindow(at("2026-10-30T22:00"), at("2026-10-31T06:00")), window)
    }

    @Test
    fun `midnight-crossing window does not start on an unselected day`() {
        // Saturday 23:00 is after the Friday window ended and Saturday isn't a start day.
        val s = schedule("22:00", "06:00", days = setOf(FRIDAY))
        assertNull(ScheduleCalculator.activeWindow(s, at("2026-10-31T23:00"), la))
    }

    @Test
    fun `next start skips the weekend`() {
        val next = ScheduleCalculator.nextStart(schedule("08:00", "18:00"), at("2026-10-30T19:00"), la)
        assertEquals(at("2026-11-02T08:00"), next) // Friday evening → Monday morning
    }

    @Test
    fun `next start while inside a window is the following day's start`() {
        val next = ScheduleCalculator.nextStart(schedule("08:00", "18:00"), at("2026-10-29T12:00"), la)
        assertEquals(at("2026-10-30T08:00"), next)
    }

    @Test
    fun `next start a full week away is found`() {
        val s = schedule("08:00", "18:00", days = setOf(MONDAY))
        // Monday 2026-11-02 at 09:00, after this week's start; next is the Monday after.
        assertEquals(at("2026-11-09T08:00"), ScheduleCalculator.nextStart(s, at("2026-11-02T09:00"), la))
    }

    @Test
    fun `DST spring forward - start inside the gap moves forward by the gap`() {
        // 2026-03-08 (Sunday) clocks jump 02:00 → 03:00 in Los Angeles; 02:30 doesn't exist.
        val s = schedule("02:30", "10:00", days = setOf(SUNDAY))
        val window = ScheduleCalculator.activeWindow(s, at("2026-03-08T09:00"), la)!!
        assertEquals(at("2026-03-08T03:30"), window.start)
        assertEquals(Duration.ofMinutes(390), Duration.between(window.start, window.end))
    }

    @Test
    fun `DST spring forward - window spanning the jump is one hour shorter in real time`() {
        val s = schedule("01:00", "05:00", days = setOf(SUNDAY))
        val window = ScheduleCalculator.activeWindow(s, at("2026-03-08T04:00"), la)!!
        assertEquals(Duration.ofHours(3), Duration.between(window.start, window.end))
    }

    @Test
    fun `DST fall back - window spanning the repeat is one hour longer in real time`() {
        // 2026-11-01 (Sunday) clocks fall back 02:00 → 01:00 in Los Angeles.
        val s = schedule("00:00", "04:00", days = setOf(SUNDAY))
        val window = ScheduleCalculator.activeWindow(s, at("2026-11-01T03:00"), la)!!
        assertEquals(Duration.ofHours(5), Duration.between(window.start, window.end))
    }

    @Test
    fun `DST fall back - ambiguous start time uses the first occurrence`() {
        val s = schedule("01:30", "06:00", days = setOf(SUNDAY))
        val window = ScheduleCalculator.activeWindow(s, at("2026-11-01T05:00"), la)!!
        // First 01:30 is still PDT (UTC-7) → 08:30Z.
        assertEquals(Instant.parse("2026-11-01T08:30:00Z"), window.start)
    }

    @Test
    fun `time zone change - the same instant is judged by the new zone's local time`() {
        // 2026-10-29T12:00 in LA is 2026-10-30T04:00 in Tokyo (Friday, before 08:00).
        val s = schedule("08:00", "18:00")
        val instant = at("2026-10-29T12:00")
        assertEquals(at("2026-10-29T08:00"), ScheduleCalculator.activeWindow(s, instant, la)?.start)
        assertNull(ScheduleCalculator.activeWindow(s, instant, tokyo))
        assertEquals(at("2026-10-30T08:00", tokyo), ScheduleCalculator.nextStart(s, instant, tokyo))
    }

    @Test
    fun `every-day overnight window is continuous across consecutive nights`() {
        val s = schedule("22:00", "06:00", days = DayOfWeek.entries.toSet())
        val night1 = ScheduleCalculator.activeWindow(s, at("2026-10-31T05:59"), la)!!
        val night2 = ScheduleCalculator.activeWindow(s, at("2026-10-31T22:00"), la)!!
        assertEquals(at("2026-10-31T06:00"), night1.end)
        assertEquals(at("2026-10-31T22:00"), night2.start)
        assertNull(ScheduleCalculator.activeWindow(s, at("2026-10-31T12:00"), la))
        assertEquals(SATURDAY, LocalDateTime.parse("2026-10-31T00:00").dayOfWeek)
    }
}
