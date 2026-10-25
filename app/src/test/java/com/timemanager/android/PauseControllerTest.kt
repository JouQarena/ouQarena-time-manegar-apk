package com.timemanager.android

import com.timemanager.android.domain.PauseController
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PauseControllerTest {
    @Test fun pauseValid() {
        var now = Instant.parse("2026-01-05T10:00:00Z")
        val c = PauseController({ now }, { listOf("StrictRule") })
        assertFalse(c.isValidMinutes(0))
        assertTrue(c.isValidMinutes(60))
        assertFalse(c.isValidMinutes(500))
        val until = c.pauseForMinutes(30)
        val state = c.resolve(until)
        assertTrue(state.active)
        assertEquals(30*60, state.remainingSeconds)
        assertEquals(listOf("StrictRule"), state.exemptRules)
        // Expire
        now = Instant.parse("2026-01-05T10:31:00Z")
        val expired = c.resolve(until)
        assertFalse(expired.active)
    }

    @Test fun invalidThrows() {
        val c = PauseController()
        try { c.pauseForMinutes(0); fail() } catch (_: IllegalArgumentException) {}
    }
}
