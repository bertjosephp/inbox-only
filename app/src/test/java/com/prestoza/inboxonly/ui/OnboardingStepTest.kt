package com.prestoza.inboxonly.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingStepTest {
    @Test
    fun `steps run in order and finish when all are done`() {
        assertEquals(OnboardingStep.ACCESSIBILITY, nextOnboardingStep(false, false, false))
        assertEquals(OnboardingStep.BATTERY, nextOnboardingStep(true, false, false))
        assertEquals(OnboardingStep.TILE, nextOnboardingStep(true, true, false))
        assertNull(nextOnboardingStep(true, true, true))
    }

    @Test
    fun `accessibility is required even if later steps were done first`() {
        assertEquals(OnboardingStep.ACCESSIBILITY, nextOnboardingStep(false, true, true))
    }
}
