package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TileStatusFormatterTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = at("2026-10-29T12:00:00")

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun status(
        state: FocusState,
        blocked: Int = 0,
        blocker: BlockerHealth = BlockerHealth.RUNNING,
        use24Hour: Boolean = false,
    ) = TileStatusFormatter.status(state, now, zone, blocked, blocker, use24Hour)

    @Test
    fun `manual with timer shows hours and minutes left`() {
        assertEquals(TileStatus(true, "1h 12m left"), status(FocusState.Manual(at("2026-10-29T13:12:00"))))
    }

    @Test
    fun `remaining time rounds up to the next minute`() {
        assertEquals("1m left", status(FocusState.Manual(at("2026-10-29T12:00:01"))).subtitle)
        assertEquals("30m left", status(FocusState.Manual(at("2026-10-29T12:29:30"))).subtitle)
    }

    @Test
    fun `whole hours drop the minutes`() {
        assertEquals("2h left", status(FocusState.Manual(at("2026-10-29T14:00:00"))).subtitle)
    }

    @Test
    fun `open-ended manual shows the blocked count, or On`() {
        assertEquals(TileStatus(true, "On"), status(FocusState.Manual(null)))
        assertEquals(TileStatus(true, "14 blocked"), status(FocusState.Manual(null), blocked = 14))
    }

    @Test
    fun `scheduled shows the end time in 12 or 24 hour format`() {
        val scheduled = FocusState.Scheduled(at("2026-10-29T18:00:00"))
        assertEquals(TileStatus(true, "Until 6:00 PM"), status(scheduled))
        assertEquals("Until 18:00", status(scheduled, use24Hour = true).subtitle)
    }

    @Test
    fun `break is inactive and shows time until focus resumes`() {
        val onBreak = FocusState.Break(at("2026-10-29T18:00:00"), at("2026-10-29T12:04:00"))
        assertEquals(TileStatus(false, "Break · 4m"), status(onBreak))
    }

    @Test
    fun `off shows today's blocked count or nothing`() {
        assertEquals(TileStatus(false, null), status(FocusState.Off))
        assertEquals(TileStatus(false, "14 blocked"), status(FocusState.Off, blocked = 14))
    }

    @Test
    fun `missing setup overrides everything`() {
        assertEquals(TileStatus(false, "Set up"), status(FocusState.Manual(null), blocker = BlockerHealth.NOT_ENABLED))
    }

    @Test
    fun `a blocker that an update left stopped asks for a restart`() {
        assertEquals(
            TileStatus(false, "Restart blocker"),
            status(FocusState.Manual(null), blocker = BlockerHealth.STOPPED),
        )
    }

    @Test
    fun `strict mode message names the end time`() {
        assertEquals(
            "Strict mode: Focus until 6:00 PM",
            TileStatusFormatter.strictModeMessage(at("2026-10-29T18:00:00"), zone, use24Hour = false),
        )
    }
}
