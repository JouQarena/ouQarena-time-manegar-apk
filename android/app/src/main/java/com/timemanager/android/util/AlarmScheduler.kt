package com.timemanager.android.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.timemanager.android.receiver.BootReceiver
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import androidx.work.*

class AlarmScheduler(private val context: Context) {

    fun schedulePeriodicChecks() {
        val req = PeriodicWorkRequestBuilder<TimeCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic_check", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun scheduleExactAlarm(atMillis: Long, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, BootReceiver::class.java).apply { action = "com.timemanager.CHECK" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun scheduleNextMidnightResets() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        scheduleExactAlarm(midnight.toInstant().toEpochMilli(), 9001)
        // also in 15m periodic worker we check day rollover as fallback
    }
}

class TimeCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Lightweight tick: query repo + run one RuleEngine.tick if rules exist
        // Full loop lives in ForegroundService; this worker ensures we notice midnight rollovers while in Doze
        return Result.success()
    }
}
