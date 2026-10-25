package com.timemanager.android

import com.timemanager.android.domain.TimeUtils
import org.junit.Assert.*
import org.junit.Test

class TimeUtilsTest {

    @Test fun formatDuration() {
        assertEquals("0s", TimeUtils.formatDuration(0))
        assertEquals("45s", TimeUtils.formatDuration(45))
        assertEquals("1m", TimeUtils.formatDuration(60))
        assertEquals("31m", TimeUtils.formatDuration(31*60))
        assertEquals("1h 32m", TimeUtils.formatDuration(3600+32*60))
        assertEquals("2h", TimeUtils.formatDuration(2*3600))
        assertEquals("1d 3h", TimeUtils.formatDuration(27*3600))
    }

    @Test fun classifySkewSane() {
        assertNull(TimeUtils.classifySkew(10.0, 10.0))
        assertNull(TimeUtils.classifySkew(59.0, 0.0))
        assertNotNull(TimeUtils.classifySkew(61.0, 0.0))
    }

    @Test fun localMidnightsBetween() {
        // Start Mon 23:00, end Tue 01:00 -> one midnight in between
        val start = java.time.ZonedDateTime.of(2026,1,5,23,0,0,0, java.time.ZoneId.systemDefault()).toEpochSecond().toDouble()
        val end = java.time.ZonedDateTime.of(2026,1,6,1,0,0,0, java.time.ZoneId.systemDefault()).toEpochSecond().toDouble()
        val mids = TimeUtils.localMidnightsBetween(start, end)
        assertEquals(1, mids.size)
    }

    @Test fun localMidnightsBetweenNone() {
        val s = 1000.0; val e = 1000.0
        assertTrue(TimeUtils.localMidnightsBetween(s,e).isEmpty())
    }
}
