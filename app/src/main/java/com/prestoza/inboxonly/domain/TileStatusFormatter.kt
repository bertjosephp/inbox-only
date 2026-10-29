// TileStatusFormatter: decides what the Quick Settings tile shows (on/off plus a short subtitle like
// "1h 12m left" or "Break · 4m"). Pure so every wording rule is unit tested; the tile service just
// copies the result onto the tile.
package com.prestoza.inboxonly.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the tile should display. [subtitle] null means "let the system show its default". */
data class TileStatus(
    val active: Boolean,
    val subtitle: String?,
)

/** Builds [TileStatus] and the strict-mode toast text. English only (personal app). */
object TileStatusFormatter {
    /**
     * @param state the reconciled focus state.
     * @param blockedToday today's block count, shown when there's nothing more useful to say.
     * @param setupNeeded true when the accessibility service isn't enabled, so Focus can't work.
     * @param use24Hour the device's clock preference.
     */
    fun status(
        state: FocusState,
        now: Instant,
        zone: ZoneId,
        blockedToday: Int,
        setupNeeded: Boolean,
        use24Hour: Boolean,
    ): TileStatus {
        if (setupNeeded) return TileStatus(active = false, subtitle = "Set up")
        return when (state) {
            FocusState.Off -> {
                TileStatus(false, blockedLabel(blockedToday))
            }

            is FocusState.Manual -> {
                TileStatus(
                    true,
                    state.until?.let { "${remaining(now, it)} left" } ?: blockedLabel(blockedToday) ?: "On",
                )
            }

            is FocusState.Scheduled -> {
                TileStatus(true, "Until ${clockTime(state.until, zone, use24Hour)}")
            }

            is FocusState.Break -> {
                TileStatus(false, "Break · ${remaining(now, state.resumeAt)}")
            }
        }
    }

    /** Toast shown when strict mode refuses a break, e.g. "Strict mode: Focus until 6:00 PM". */
    fun strictModeMessage(
        until: Instant,
        zone: ZoneId,
        use24Hour: Boolean,
    ): String = "Strict mode: Focus until ${clockTime(until, zone, use24Hour)}"

    private fun blockedLabel(count: Int): String? = if (count > 0) "$count blocked" else null

    // Rounded up so "0m left" never appears while Focus is still on.
    private fun remaining(
        now: Instant,
        end: Instant,
    ): String {
        val seconds = Duration.between(now, end).seconds.coerceAtLeast(0)
        val totalMinutes = ((seconds + 59) / 60).coerceAtLeast(1)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    private fun clockTime(
        instant: Instant,
        zone: ZoneId,
        use24Hour: Boolean,
    ): String {
        val pattern = if (use24Hour) "H:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.US).format(instant.atZone(zone))
    }
}
