package com.timemanager.android.domain

import java.time.Instant

/**
 * Usage tracker — port of app/core/tracking/tracker.py
 * Pure logic over DAO calls; monotonic clock is elapsedRealtime().
 */

data class Activity(val active: Boolean, val sessionType: String = "FOREGROUND")
data class TrackInfo(
    val ruleId: Long,
    val trackedActive: Boolean,
    val sessionId: Long?,
    val sessionSeconds: Int, // continuous total spanning midnight splits
    val day: String,
    val attributedSeconds: Int // seconds attributed by THIS tick
)

private data class Active(
    var sessionId: Long,
    var sessionType: String,
    var day: String,
    var continuousSeconds: Int = 0,
    var segmentSeconds: Int = 0,
    var unpersisted: Int = 0,
    var lastMono: Double = 0.0,
    var lastWall: Double = 0.0
)

/**
 * DAO interface (implemented by Room DAO; faked in tests)
 */
interface TrackerDao {
    fun openSession(ruleId: Long, sessionType: String, startedAt: String): Long
    fun heartbeatSession(sessionId: Long, durationSeconds: Int)
    fun closeSession(sessionId: Long, durationSeconds: Int)
    fun addDaily(ruleId: Long, day: String, seconds: Int)
    fun getDaily(ruleId: Long, day: String): Int
    fun logClock(note: String)
}

class Tracker(
    private val dao: TrackerDao,
    private val wallClock: () -> Double = { System.currentTimeMillis() / 1000.0 },
    private val monoClock: () -> Double = { android.os.SystemClock.elapsedRealtime() / 1000.0 },
    private val persistSeconds: Int = 5,
    private val maxSingleDelta: Int = 600
) {
    companion object { const val SKEW_TOLERANCE = 60.0 }

    private val active = mutableMapOf<Long, Active>()
    private var baseline: Pair<Double, Double>? = null

    fun poll(activities: Map<Long, Activity>): Map<Long, TrackInfo> {
        val nowWall = wallClock()
        val nowMono = monoClock()
        if (baseline == null) {
            baseline = nowWall to nowMono
            return pollInner(activities, nowWall, nowMono, fresh = true)
        }
        val lastWall = baseline!!.first
        val lastMono = baseline!!.second
        val note = TimeUtils.classifySkew(nowWall - lastWall, nowMono - lastMono, SKEW_TOLERANCE)
        if (note != null) {
            dao.logClock("tracker anomaly, sessions safely closed: $note")
            closeAllInternal()
            baseline = nowWall to nowMono
            return pollInner(activities, nowWall, nowMono, fresh = true)
        }
        baseline = nowWall to nowMono
        return pollInner(activities, nowWall, nowMono, fresh = false)
    }

    private fun pollInner(activities: Map<Long, Activity>, nowWall: Double, nowMono: Double, fresh: Boolean): Map<Long, TrackInfo> {
        val merged = activities.toMutableMap()
        for (id in active.keys) merged.putIfAbsent(id, Activity(active = false))
        val today = TimeUtils.localDayStrFromEpoch(nowWall)
        val infos = mutableMapOf<Long, TrackInfo>()
        for ((ruleId, act) in merged) {
            val st = active[ruleId]
            if (st == null) {
                if (act.active) {
                    val sid = dao.openSession(ruleId, act.sessionType, Instant.ofEpochSecond(nowWall.toLong()).toString())
                    active[ruleId] = Active(sessionId = sid, sessionType = act.sessionType, day = today, lastMono = nowMono, lastWall = nowWall)
                    infos[ruleId] = TrackInfo(ruleId, true, sid, 0, today, 0)
                } else {
                    infos[ruleId] = TrackInfo(ruleId, false, null, 0, today, 0)
                }
                continue
            }
            var delta = if (fresh) 0 else maxOf(0, (nowMono - st.lastMono).toInt())
            if (delta > maxSingleDelta) {
                dao.logClock("rule $ruleId: single-tick delta ${delta}s capped to ${maxSingleDelta}s")
                delta = maxSingleDelta
            }
            var attributed = 0
            if (delta > 0) attributed = attribute(ruleId, st, st.lastWall, nowWall, delta)
            st.lastMono = nowMono; st.lastWall = nowWall
            if (!act.active) {
                val cont = st.continuousSeconds
                closeRuleInternal(ruleId, st)
                infos[ruleId] = TrackInfo(ruleId, false, null, cont, st.day, attributed)
            } else {
                if (st.unpersisted >= persistSeconds) persistRule(ruleId, st)
                infos[ruleId] = TrackInfo(ruleId, true, st.sessionId, st.continuousSeconds, st.day, attributed)
            }
        }
        return infos
    }

    private fun attribute(ruleId: Long, st: Active, lastWall: Double, nowWall: Double, delta: Int): Int {
        val cuts = mutableListOf(lastWall)
        if (nowWall > lastWall) cuts += TimeUtils.localMidnightsBetween(lastWall, nowWall)
        cuts.add(nowWall)
        val totalWall = nowWall - lastWall
        var acc = 0
        for (i in 0 until cuts.size - 1) {
            val a = cuts[i]; val b = cuts[i+1]
            val share = if (i < cuts.size - 2 && totalWall > 0) (delta * (b - a) / totalWall).toInt() else delta - acc
            if (share <= 0) continue
            val day = TimeUtils.localDayStrFromEpoch(if (b > a) a else nowWall)
            if (day != st.day) rollDay(ruleId, st, day, a)
            st.continuousSeconds += share; st.segmentSeconds += share; st.unpersisted += share; acc += share
        }
        return delta
    }

    private fun rollDay(ruleId: Long, st: Active, newDay: String, atEpoch: Double) {
        persistRule(ruleId, st)
        dao.closeSession(st.sessionId, st.segmentSeconds)
        st.sessionId = dao.openSession(ruleId, st.sessionType, Instant.ofEpochSecond(atEpoch.toLong()).toString())
        st.day = newDay; st.segmentSeconds = 0
    }

    private fun persistRule(ruleId: Long, st: Active) {
        if (st.unpersisted > 0) { dao.addDaily(ruleId, st.day, st.unpersisted); st.unpersisted = 0 }
        dao.heartbeatSession(st.sessionId, st.segmentSeconds)
    }

    private fun closeRuleInternal(ruleId: Long, st: Active) {
        persistRule(ruleId, st); dao.closeSession(st.sessionId, st.segmentSeconds); active.remove(ruleId)
    }

    private fun closeAllInternal() {
        for (id in active.keys.toList()) {
            val st = active[id] ?: continue
            persistRule(id, st); dao.closeSession(st.sessionId, st.segmentSeconds)
        }
        active.clear()
    }

    fun todayTotal(ruleId: Long, day: String? = null): Int {
        val d = day ?: TimeUtils.localDayStrFromEpoch(wallClock())
        var total = dao.getDaily(ruleId, d)
        val st = active[ruleId]; if (st != null && st.day == d) total += st.unpersisted
        return total
    }

    fun sessionSeconds(ruleId: Long): Int = active[ruleId]?.continuousSeconds ?: 0
    fun openRuleIds(): List<Long> = active.keys.sorted()
    fun flush() { for ((id, st) in active) persistRule(id, st) }
    fun closeAll() { closeAllInternal() }
    fun closeRule(ruleId: Long): Boolean {
        val st = active[ruleId] ?: return false
        closeRuleInternal(ruleId, st); return true
    }
    fun notifySuspend() {
        dao.logClock("suspend: open sessions sealed, gap will not accrue")
        closeAllInternal(); baseline = null
    }
}
