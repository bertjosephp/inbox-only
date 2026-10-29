package com.prestoza.inboxonly.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InspectorFormatTest {
    @Test
    fun `short text is shown`() {
        assertEquals("Reels", InspectorFormat.redact("Reels"))
    }

    @Test
    fun `text over 30 chars is replaced by its length`() {
        val message = "hey are you coming to dinner tonight or not?"
        assertEquals("‹${message.length} chars›", InspectorFormat.redact(message))
    }

    @Test
    fun `exactly 30 chars is still shown`() {
        val thirty = "a".repeat(30)
        assertEquals(thirty, InspectorFormat.redact(thirty))
    }

    @Test
    fun `blank and null are dropped`() {
        assertNull(InspectorFormat.redact(null))
        assertNull(InspectorFormat.redact("  "))
    }

    @Test
    fun `node line includes id, description and selection with indentation`() {
        val line =
            InspectorFormat.nodeLine(
                depth = 2,
                className = "android.widget.FrameLayout",
                viewId = "fixture:id/tab",
                contentDescription = "Reels",
                text = null,
                selected = true,
            )
        assertEquals("    FrameLayout id=fixture:id/tab desc=\"Reels\" [selected]", line)
    }

    @Test
    fun `node with nothing useful is skipped`() {
        assertNull(InspectorFormat.nodeLine(0, "android.view.View", null, null, null, false))
    }

    @Test
    fun `long message text in a node line is redacted`() {
        val line = InspectorFormat.nodeLine(0, "TextView", null, null, "x".repeat(120), false)
        assertEquals("TextView text=\"‹120 chars›\"", line)
    }
}
