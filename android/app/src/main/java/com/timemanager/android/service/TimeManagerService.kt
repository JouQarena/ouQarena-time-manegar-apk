package com.timemanager.android.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.timemanager.android.data.AppRepository
import com.timemanager.android.domain.*
import com.timemanager.android.monitor.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZonedDateTime

/**
 * ForegroundService that owns the monitor loop while enforcement is live or a schedule is active.
 * When no rules need live monitoring, WorkManager takes over (battery-friendly).
 * This mirrors the desktop's single AgentService owner concept.
 */
class TimeManagerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "monitor"
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Monitoring…"))
        job?.cancel()
        job = scope.launch { monitorLoop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        val repo = AppRepository(this@TimeManagerService)
        val usageMonitor = UsageMonitor(this@TimeManagerService)
        // Simple DAO adapter for Tracker (Room already provides daily/session ops)
        val trackerDao = object : TrackerDao {
            override fun openSession(ruleId: Long, sessionType: String, startedAt: String): Long =
                kotlinx.coroutines.runBlocking { repo.openSession(ruleId, sessionType, startedAt) }
            override fun heartbeatSession(sessionId: Long, durationSeconds: Int) =
                kotlinx.coroutines.runBlocking { repo.heartbeatSession(sessionId, durationSeconds) }
            override fun closeSession(sessionId: Long, durationSeconds: Int) =
                kotlinx.coroutines.runBlocking { repo.closeSession(sessionId, durationSeconds) }
            override fun addDaily(ruleId: Long, day: String, seconds: Int) =
                kotlinx.coroutines.runBlocking { repo.addDaily(ruleId, day, seconds) }
            override fun getDaily(ruleId: Long, day: String): Int =
                kotlinx.coroutines.runBlocking { repo.getDaily(ruleId, day) }
            override fun logClock(note: String) =
                kotlinx.coroutines.runBlocking { repo.logClock(note) }
        }
        val tracker = Tracker(trackerDao)
        val ruleEngine = RuleEngine(repo, tracker)
        val resolver = ActivityResolver()
        val executor = EnforcementExecutor(this@TimeManagerService)

        while (currentCoroutineContext().isActive) {
            try {
                val rules = repo.listRules()
                val fg = usageMonitor.currentForegroundPackage()
                val resolved = resolver.resolve(rules, fg, idleSeconds = null, webActive = null)
                val outcomes = ruleEngine.tick(resolved.activities)
                var anyEnforced = false
                for (o in outcomes) {
                    val act = o.decision.actionToExecute
                    if (act != null) {
                        executor.execute(o.rule, act, fg)
                        anyEnforced = true
                    }
                    // Post warning notifications
                    for (w in o.decision.warningsToFire) {
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        val n = NotificationCompat.Builder(this@TimeManagerService, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("Time Manager")
                            .setContentText("${o.rule.name}: ${TimeUtils.formatDuration(w)} remaining")
                            .build()
                        nm.notify((o.rule.id?.toInt() ?: 2000) + w, n)
                    }
                }
                // Update foreground notification
                val status = if (anyEnforced) "Enforcement active" else "Monitoring ${rules.size} rules"
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification(status))

                // If no rules are enabled, stop self to save battery (WorkManager will wake us)
                val enabled = rules.any { it.enabled }
                if (!enabled) {
                    delay(60_000) // check again in a minute
                } else {
                    delay(3_000) // 3s polling while FG service
                }
            } catch (e: Exception) {
                delay(5_000)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.timemanager.android.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Time Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Monitoring", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Shows while Time Manager is monitoring usage"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}
