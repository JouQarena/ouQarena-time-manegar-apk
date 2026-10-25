package com.timemanager.android.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timemanager.android.monitor.UsageMonitor

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val usageMonitor = remember { UsageMonitor(ctx) }

    var usageGranted by remember { mutableStateOf(usageMonitor.hasUsageAccess()) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var canExactAlarm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
        else true)
    }
    // Refresh on resume
    LaunchedEffect(Unit) {
        usageGranted = usageMonitor.hasUsageAccess()
        canDrawOverlays = Settings.canDrawOverlays(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canExactAlarm = (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Permissions & Enforcement Status") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Time Manager shows exactly what it can and cannot enforce on this device. No feature is silently weakened.", style = MaterialTheme.typography.bodyMedium)

            PermissionRow(
                title = "Usage Access (UsageStatsManager)",
                desc = "Required to know which app is in the foreground. Without it, app rules never count.",
                granted = usageGranted,
                status = if (usageGranted) "Monitoring possible ✓" else "Monitoring blocked ✗",
                onFix = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
            PermissionRow(
                title = "Accessibility Service (optional but recommended)",
                desc = "Improves foreground detection latency and enables website domain extraction from browser address bars.",
                granted = null, status = "Check Settings → Accessibility → Time Manager",
                onFix = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
            PermissionRow(
                title = "Draw over other apps (SYSTEM_ALERT_WINDOW)",
                desc = "Shows the 'Time's up' overlay when a limit is reached. Without it, enforcement falls back to notification + home.",
                granted = canDrawOverlays,
                status = if (canDrawOverlays) "Overlay blocking possible ✓" else "Overlay not possible ✗",
                onFix = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
            )
            PermissionRow(
                title = "Exact alarms (Android 12+)",
                desc = "Required for precise schedule transitions and enforcement deadlines. Without it, timings fall back to inexact 15-min windows.",
                granted = canExactAlarm,
                status = if (canExactAlarm) "Exact timers possible ✓" else "Exact timers blocked → inexact fallback",
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            )
            PermissionRow(
                title = "Notifications (Android 13+)",
                desc = "Warnings and enforcement notices. Denial is not silent — dashboard shows enforcement via other channels.",
                granted = null, status = "Grant POST_NOTIFICATIONS in system settings if you denied it.",
                onFix = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) }) }
            )
            PermissionRow(
                title = "Battery optimisation exemption",
                desc = "OEM battery killers break background tracking. Exempt the app, otherwise WorkManager may be delayed 15 min.",
                granted = null, status = "Exempt via: Settings → Battery → Unrestricted",
                onFix = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            )
            PermissionRow(
                title = "Device Owner / setPackagesSuspended (optional, strong enforcement)",
                desc = "Normal apps cannot force-close other foreground apps. For hard blocks, provision the app as Device Owner via QR or ADB (see PERMISSIONS.md). Without it, best-effort overlay+home is used.",
                granted = null, status = "Not active by default — setup requires ADB/QR",
                onFix = {}
            )

            Divider()
            Text("Enforcement honesty", style = MaterialTheme.typography.titleSmall)
            Text(
                "• Every enforcement event logs EXECUTED vs DEFERRED vs PROTECTED_SKIP with detail.\n" +
                "• Website blocking on Android is best-effort (Accessibility domain extraction). DNS/VPN methods need root/device-owner and are documented as such.\n" +
                "• Killing the app always works once — the next launch reports UNEXPECTED_STOP; watchdog revives within 15 min via WorkManager (not per-minute like Windows Task Scheduler).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean?, status: String, onFix: () -> Unit) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted == true) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                else if (granted == false) Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(desc, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            if (granted != true) {
                TextButton(onClick = onFix) { Text("Fix in Settings →") }
            }
        }
    }
}
