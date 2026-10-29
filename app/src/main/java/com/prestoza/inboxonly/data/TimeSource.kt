// TimeSource: where "now" and the current time zone come from. A seam so tests and the domain layer
// never call the system clock directly.
package com.prestoza.inboxonly.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Supplies the current instant and time zone. */
interface TimeSource {
    /** The current instant. */
    fun now(): Instant

    /** The current zone. Read on every call because the user can change it at any time. */
    fun zone(): ZoneId

    /** Today's date in the current zone. */
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

/** The real device clock. */
object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
