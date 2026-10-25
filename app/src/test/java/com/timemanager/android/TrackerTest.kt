package com.timemanager.android

import com.timemanager.android.domain.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tracker tests with fake DAO — pins monotonic/midnight/skew behaviour.
 */
class TrackerTest {

    private class FakeDao : TrackerDao {
        val sessions = mutableMapOf<Long, Int>() // sessionId -> duration
        val daily = mutableMapOf<Pair<Long,String>, Int>()
        var nextId = 1L
        val clockLogs = mutableListOf<String>()
        override fun openSession(ruleId: Long, sessionType: String, startedAt: String): Long {
            val id = nextId++; sessions[id]=0; return id
        }
        override fun heartbeatSession(sessionId: Long, durationSeconds: Int) { sessions[sessionId]=durationSeconds }
        override fun closeSession(sessionId: Long, durationSeconds: Int) { sessions[sessionId]=durationSeconds }
        override fun addDaily(ruleId: Long, day: String, seconds: Int) { daily[ruleId to day] = (daily[ruleId to day] ?: 0) + seconds }
        override fun getDaily(ruleId: Long, day: String): Int = daily[ruleId to day] ?: 0
        override fun logClock(note: String) { clockLogs.add(note) }
    }

    @Test fun singleSessionCountsMonotonicDelta() {
        val dao = FakeDao()
        var wall = 1_000_000.0
        var mono = 0.0
        val t = Tracker(dao, wallClock = { wall }, monoClock = { mono })
        // First poll opens
        var res = t.poll(mapOf(1L to Activity(true)))
        assertTrue(res[1L]!!.trackedActive)
        assertEquals(0, res[1L]!!.attributedSeconds)
        // Advance 5s
        mono = 5.0; wall += 5.0
        res = t.poll(mapOf(1L to Activity(true)))
        assertEquals(5, res[1L]!!.attributedSeconds)
        // Stop
        mono = 7.0; wall += 2.0
        res = t.poll(mapOf(1L to Activity(false)))
        assertFalse(res[1L]!!.trackedActive)
        assertEquals(2, res[1L]!!.attributedSeconds)
    }

    @Test fun skewClosesSessions() {
        val dao = FakeDao()
        var wall = 1_000_000.0; var mono = 0.0
        val t = Tracker(dao, wallClock = { wall }, monoClock = { mono })
        t.poll(mapOf(1L to Activity(true)))
        mono = 2.0; wall += 2.0
        t.poll(mapOf(1L to Activity(true)))
        // Simulate clock jump: wall jumps +100s vs mono +2s -> skew 98s >60
        mono += 2.0; wall += 100.0
        val res = t.poll(mapOf(1L to Activity(true)))
        // Fresh session after anomaly should start with 0
        assertEquals(0, res[1L]!!.attributedSeconds)
        assertTrue(dao.clockLogs.any { it.contains("anomaly") })
    }

    @Test fun todayTotalIncludesUnflushed() {
        val dao = FakeDao()
        var wall = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()).toEpochSecond().toDouble()
        var mono = 0.0
        val t = Tracker(dao, wallClock = { wall }, monoClock = { mono }, persistSeconds = 100) // high so not flushed
        t.poll(mapOf(1L to Activity(true)))
        mono = 10.0; wall += 10.0
        t.poll(mapOf(1L to Activity(true)))
        val day = TimeUtils.localDayStrFromEpoch(wall)
        assertEquals(10, t.todayTotal(1L, day))
        // Daily DAO still 0 because not flushed yet — todayTotal uses unpersisted
        assertEquals(0, dao.daily[1L to day] ?: 0)
        t.flush()
        assertEquals(10, dao.daily[1L to day])
    }
}
