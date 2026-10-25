package com.timemanager.android

import com.timemanager.android.domain.Schedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {

    private fun at(day: DayOfWeek, h: Int, m: Int): ZonedDateTime {
        // Pick a known week: Mon 2026-01-05 is Monday
        val base = LocalDate.of(2026, 1, 5) // Monday
        val offset = day.ordinal - DayOfWeek.MONDAY.ordinal
        val date = base.plusDays(offset.toLong())
        return ZonedDateTime.of(date, LocalTime.of(h, m), ZoneId.systemDefault())
    }

    @Test fun wholeDayEverydayAlwaysActive() {
        val s = Schedule(days = null, windows = emptyList())
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 12, 0)))
        assertTrue(s.isActive(at(DayOfWeek.SUNDAY, 23, 59)))
    }

    @Test fun weekdayOnly() {
        val s = Schedule(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            windows = emptyList()
        )
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 10, 0)))
        assertFalse(s.isActive(at(DayOfWeek.SATURDAY, 10, 0)))
        assertFalse(s.isActive(at(DayOfWeek.SUNDAY, 10, 0)))
    }

    @Test fun windowSameDay() {
        val s = Schedule(days = null, windows = listOf(LocalTime.of(8,0) to LocalTime.of(22,0)))
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 9, 0)))
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 21, 59)))
        assertFalse(s.isActive(at(DayOfWeek.MONDAY, 22, 0)))
        assertFalse(s.isActive(at(DayOfWeek.MONDAY, 7, 59)))
    }

    @Test fun overnightWindow() {
        val s = Schedule(days = null, windows = listOf(LocalTime.of(22,0) to LocalTime.of(2,0)))
        // Evening part belongs to today
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 22, 30)))
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 23, 59)))
        // Morning spill belongs to yesterday's window
        assertTrue(s.isActive(at(DayOfWeek.TUESDAY, 1, 30)))
        assertTrue(s.isActive(at(DayOfWeek.TUESDAY, 1, 59)))
        assertFalse(s.isActive(at(DayOfWeek.TUESDAY, 2, 0)))
        assertFalse(s.isActive(at(DayOfWeek.MONDAY, 21, 59)))
    }

    @Test fun overnightWithDayFilter() {
        // Weekdays overnight: Mon 22:00 -> Tue 02:00 should count, but Sat 22:00 -> Sun 02:00 should not if WEEKDAYS only
        val s = Schedule(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            windows = listOf(LocalTime.of(22,0) to LocalTime.of(2,0))
        )
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 23, 0))) // Mon evening part ok
        assertTrue(s.isActive(at(DayOfWeek.TUESDAY, 1, 0))) // Tue early belongs to Mon's window
        // Saturday night should NOT be active even though 1am Sunday spills from Saturday — Saturday not in days
        assertFalse(s.isActive(at(DayOfWeek.SATURDAY, 23, 0)))
        assertFalse(s.isActive(at(DayOfWeek.SUNDAY, 1, 0)))
    }

    @Test fun parseRoundTrip() {
        val s = Schedule(days = null, windows = listOf(LocalTime.of(8,0) to LocalTime.of(22,0)))
        val json = s.toJson()
        val parsed = Schedule.parse(json)
        assertNotNull(parsed)
        assertEquals(s.days, parsed!!.days)
        assertEquals(s.windows, parsed.windows)
    }

    @Test fun parseEverydayAndWeekdays() {
        val json1 = """{"days":"EVERYDAY","windows":[]}"""
        assertNull(Schedule.parse(json1)?.days)
        val json2 = """{"days":"WEEKDAYS","windows":[["09:00","17:00"]]}"""
        val p = Schedule.parse(json2)!!
        assertEquals(5, p.days!!.size)
        assertEquals(1, p.windows.size)
    }

    @Test fun overlappingWindows() {
        val s = Schedule(days = null, windows = listOf(LocalTime.of(8,0) to LocalTime.of(12,0), LocalTime.of(14,0) to LocalTime.of(18,0)))
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 9, 0)))
        assertFalse(s.isActive(at(DayOfWeek.MONDAY, 13, 0)))
        assertTrue(s.isActive(at(DayOfWeek.MONDAY, 15, 0)))
    }
}
