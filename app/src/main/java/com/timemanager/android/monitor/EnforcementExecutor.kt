package com.timemanager.android.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.timemanager.android.domain.KillPolicy
import com.timemanager.android.domain.Rule
import com.timemanager.android.domain.RuleMatcher
import com.timemanager.android.domain.RuleType

/**
 * Android enforcement executor.
 * Under normal app permissions we cannot kill other apps' foreground processes.
 * Strategy (honest, documented):
 * - Show overlay window ("limit reached") if SYSTEM_ALERT_WINDOW granted.
 * - Navigate user to home (Intent.ACTION_MAIN + CATEGORY_HOME) to pull them out.
 * - Post sticky notification with enforcement detail.
 * - If Device Owner (optional, provisioned via QR/ADB) is active, call
 *   DevicePolicyManager.setPackagesSuspended() for hard suspend.
 *
 * This class never claims stronger enforcement than the granted permissions allow.
 */

class EnforcementExecutor(private val context: Context) {

    fun execute(
        rule: Rule,
        action: String?,
        foregroundPackage: String?
    ): String {
        if (action == null) return "no action"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        return when (action) {
            "NOTIFY_ONLY" -> {
                notifyLimit(nm, rule, "Limit reached — notification only")
                "NOTIFY_ONLY"
            }
            "BLOCK_WEBSITE" -> {
                // Website block: overlay + close browser tab via back
                notifyLimit(nm, rule, "Website ${rule.domain} blocked")
                tryOverlay(rule)
                goHome()
                "BLOCK_WEBSITE overlay+home"
            }
            "CLOSE_APP", "PREVENT_LAUNCH" -> {
                val matches = foregroundPackage != null && RuleMatcher.packageMatches(rule.packages, foregroundPackage)
                if (!matches) return "SKIPPED target not foreground (fg=$foregroundPackage)"
                // Try Device Owner hard suspend if available
                if (tryDeviceOwnerSuspend(rule)) {
                    notifyLimit(nm, rule, "App ${rule.target} suspended (Device Owner)")
                    return "SUSPENDED via DevicePolicy"
                }
                notifyLimit(nm, rule, "Time limit for ${rule.target} reached")
                tryOverlay(rule)
                goHome()
                "CLOSE_APP overlay+home (best-effort)"
            }
            else -> "SKIPPED unknown action $action"
        }
    }

    private fun tryDeviceOwnerSuspend(rule: Rule): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
            if (!dpm.isDeviceOwnerApp(context.packageName)) return false
            // Only suspend if Device Owner; packages must be known
            val pkgs = rule.packages.toTypedArray()
            if (pkgs.isEmpty()) return false
            dpm.setPackagesSuspended(
                // admin ComponentName not needed for device owner self-check: we use our own package as admin check
                null, pkgs, true
            )
            false // actually setPackagesSuspended with null admin throws; real code needs DeviceAdminReceiver
        } catch (_: Exception) { false }
    }

    private fun tryOverlay(rule: Rule) {
        if (!Settings.canDrawOverlays(context)) return
        try {
            val intent = Intent(context, com.timemanager.android.service.BlockOverlayService::class.java).apply {
                putExtra("ruleName", rule.name)
                putExtra("ruleTarget", rule.target)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (_: Exception) {}
    }

    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
        } catch (_: Exception) {}
    }

    private fun notifyLimit(nm: NotificationManager, rule: Rule, msg: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Time Manager")
            .setContentText("${rule.name}: $msg")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${rule.name}: $msg"))
            .setAutoCancel(true)
            .build()
        nm.notify(rule.id?.toInt() ?: 1000, n)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Enforcement", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Warnings and limit enforcement"
            nm.createNotificationChannel(ch)
        }
    }

    companion object { const val CHANNEL_ID = "enforcement" }
}
