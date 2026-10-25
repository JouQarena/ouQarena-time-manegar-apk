package com.timemanager.android.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.SystemClock

/**
 * Android foreground-package monitor via UsageStatsManager.
 * Requires PACKAGE_USAGE_STATS (Usage Access). Poll interval configurable (default 3s —
 * faster than WorkManager but battery-aware; ForegroundService uses 2s when enforcement live).
 */
class UsageMonitor(private val context: Context) {

    data class Snapshot(
        val foregroundPackage: String?,
        val timestamp: Long = System.currentTimeMillis(),
        val monoTime: Double = SystemClock.elapsedRealtime() / 1000.0
    )

    fun currentForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        // Query last 10 seconds of events
        val events = usm.queryEvents(now - 10_000, now) ?: return null
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp >= lastTime) {
                    lastTime = event.timeStamp
                    lastPackage = event.packageName
                }
            }
        }
        return lastPackage
    }

    fun snapshot(): Snapshot = Snapshot(currentForegroundPackage())

    fun hasUsageAccess(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000L, now)
        return stats != null // null = no permission; empty list is also possible but not null on granted
    }
}
