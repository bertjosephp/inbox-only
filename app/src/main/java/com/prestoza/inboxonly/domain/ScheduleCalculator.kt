// ScheduleCalculator: turns a recurring Schedule (local times + weekdays) into concrete windows on the
// timeline. Uses java.time zoned arithmetic so midnight crossings, DST changes and time zone changes all
// come out right. Pure and stateless; the caller supplies "now" and the zone.
package com.prestoza.inboxonly.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One concrete occurrence of a schedule window, half-open: active when `start <= now < end`. */
data class ScheduleWindow(
    val start: Instant,
    val end: Instant,
) {
    /** True when [instant] falls inside this window. */
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/**
 * Computes schedule windows around a point in time.
 *
 * DST handling: a local start or end time that doesn't exist (spring-forward gap) moves forward by the
 * gap length, e.g. 02:30 becomes 03:30. A time that happens twice (fall-back overlap) uses the first one.
 * That's java.time's default for [ZonedDateTime.of], and it matches what people expect from an alarm clock.
 */
object ScheduleCalculator {
    // Windows are shorter than 24h and start on selected days, so looking one day back (for windows that
    // crossed midnight) and a week ahead (to find the next start) covers every case.
    private const val DAYS_BACK = 1L
    private const val DAYS_AHEAD = 8L

    /** The window containing [now], or null if Focus shouldn't be on because of the schedule. */
    fun activeWindow(
        schedule: Schedule,
        now: Instant,
        zone: ZoneId,
    ): ScheduleWindow? =
        windowsAround(schedule, now, zone)
            .filter { now in it }
            // Overlaps are only possible in odd DST corner cases; prefer the one that lasts longest.
            .maxByOrNull { it.end }

    /** The first window start strictly after [now], or null when the schedule can't produce one. */
    fun nextStart(
        schedule: Schedule,
        now: Instant,
        zone: ZoneId,
    ): Instant? =
        windowsAround(schedule, now, zone)
            .map { it.start }
            .filter { it > now }
            .minOrNull()

    private fun windowsAround(
        schedule: Schedule,
        now: Instant,
        zone: ZoneId,
    ): List<ScheduleWindow> {
        if (!schedule.isUsable) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        return (-DAYS_BACK..DAYS_AHEAD)
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek in schedule.days }
            .map { windowStartingOn(it, schedule, zone) }
    }

    private fun windowStartingOn(
        date: LocalDate,
        schedule: Schedule,
        zone: ZoneId,
    ): ScheduleWindow {
        val crossesMidnight = schedule.end <= schedule.start
        val endDate = if (crossesMidnight) date.plusDays(1) else date
        val start = ZonedDateTime.of(date, schedule.start, zone).toInstant()
        val end = ZonedDateTime.of(endDate, schedule.end, zone).toInstant()
        return ScheduleWindow(start, end)
    }
}
