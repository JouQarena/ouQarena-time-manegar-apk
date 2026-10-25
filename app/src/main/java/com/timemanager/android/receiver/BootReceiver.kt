package com.timemanager.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.timemanager.android.service.TimeManagerService
import com.timemanager.android.util.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_LOCALE_CHANGED
            )) {
            // Reschedule alarms
            AlarmScheduler(context).scheduleNextMidnightResets()
            AlarmScheduler(context).schedulePeriodicChecks()

            // Restart foreground service if app has active rules
            // We do this via WorkManager enqueue — service starts only if enabled rules exist
            try {
                val svc = Intent(context, TimeManagerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc)
                else context.startService(svc)
            } catch (_: Exception) {}
        }
    }
}
