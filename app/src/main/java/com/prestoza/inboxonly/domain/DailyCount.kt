// DailyCount: the "N blocked today" counter as a value. Resetting at local midnight is just a date
// comparison, so it needs no midnight alarm: a count stored for yesterday reads as zero today.
package com.prestoza.inboxonly.domain

import java.time.LocalDate

/** How many blocks happened on [date]. */
data class DailyCount(
    val date: LocalDate,
    val count: Int,
) {
    /** The count as seen on [today]: zero if the stored count is from another day. */
    fun countOn(today: LocalDate): Int = if (date == today) count else 0

    /** The counter after one more block on [today], starting fresh if the day changed. */
    fun incrementedOn(today: LocalDate): DailyCount = DailyCount(today, countOn(today) + 1)
}
