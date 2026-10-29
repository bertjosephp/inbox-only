package com.prestoza.inboxonly.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyCountTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test
    fun `count from today is returned as is`() {
        assertEquals(14, DailyCount(today, 14).countOn(today))
    }

    @Test
    fun `count from yesterday reads as zero after midnight`() {
        assertEquals(0, DailyCount(today.minusDays(1), 14).countOn(today))
    }

    @Test
    fun `increment on a new day starts from one`() {
        assertEquals(DailyCount(today, 1), DailyCount(today.minusDays(1), 14).incrementedOn(today))
    }

    @Test
    fun `increment on the same day adds one`() {
        assertEquals(DailyCount(today, 15), DailyCount(today, 14).incrementedOn(today))
    }
}
