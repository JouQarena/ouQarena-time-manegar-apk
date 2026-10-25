package com.timemanager.android.monitor

import com.timemanager.android.domain.Rule
import com.timemanager.android.domain.RuleMatcher
import com.timemanager.android.domain.RuleType

/**
 * Port of app/core/monitoring/activity.py ActivityResolver (adapted for Android packages + websites)
 */
data class ActivityPolicy(
    val enforceForegroundOnly: Boolean = true,
    val backgroundGraceSeconds: Int = 5,
    val idleGraceSeconds: Int = 0, // 0 = disabled
    val websiteGraceSeconds: Int = 3,
    val websiteStaleSeconds: Int = 15,
    val requireBrowserForeground: Boolean = true,
    val browserPackages: Set<String> = setOf(
        "com.android.chrome", "com.microsoft.emmx", "org.mozilla.firefox", "com.brave.browser",
        "com.opera.browser", "com.sec.android.app.sbrowser"
    )
)

data class ResolvedActivity(
    val activities: Map<Long, ActivityInput>,
    val detail: Map<Long, String>,
    val foregroundPackage: String? = null,
    val idleSeconds: Double? = null,
    val idleBlocked: Boolean = false
)

class ActivityResolver(
    private val policy: ActivityPolicy = ActivityPolicy()
) {
    private val lastForeground = mutableMapOf<String, Double>()
    private val webFirstSeen = mutableMapOf<String, Double>()

    fun resolve(
        rules: List<Rule>,
        foregroundPackage: String?,
        idleSeconds: Double?,
        webActive: Map<String, Double>? = null, // domain -> lastSeenMono
        nowMono: Double = android.os.SystemClock.elapsedRealtime() / 1000.0
    ): ResolvedActivity {
        val idleBlocked = policy.idleGraceSeconds > 0 && idleSeconds != null && idleSeconds >= policy.idleGraceSeconds
        val browserForeground = !policy.requireBrowserForeground || (foregroundPackage != null && foregroundPackage in policy.browserPackages)

        val activities = mutableMapOf<Long, ActivityInput>()
        val details = mutableMapOf<Long, String>()

        for (rule in rules) {
            val id = rule.id ?: continue
            when (rule.type) {
                RuleType.APPLICATION, RuleType.GAME -> {
                    val pkgMatches = foregroundPackage != null && RuleMatcher.packageMatches(rule.packages, foregroundPackage)
                    when {
                        idleBlocked -> {
                            activities[id] = ActivityInput(active = false, idleBlocked = true)
                            details[id] = "idle ${idleSeconds?.toInt()}s ≥ ${policy.idleGraceSeconds}s"
                        }
                        pkgMatches -> {
                            activities[id] = ActivityInput(active = true, sessionType = if (rule.type == RuleType.GAME) "GAME" else "FOREGROUND")
                            details[id] = "foreground $foregroundPackage matches ${rule.packages}"
                        }
                        else -> {
                            // background grace: recently foreground app still counts briefly
                            val graceKey = rule.packages.firstOrNull() ?: rule.target
                            val lastSeen = lastForeground[graceKey]
                            val inGrace = lastSeen != null && (nowMono - lastSeen) < policy.backgroundGraceSeconds
                            if (inGrace && policy.enforceForegroundOnly.not()) {
                                activities[id] = ActivityInput(active = true)
                                details[id] = "background grace for $graceKey"
                            } else {
                                activities[id] = ActivityInput(active = false)
                                details[id] = "not foreground (fg=$foregroundPackage)"
                            }
                        }
                    }
                    if (pkgMatches && foregroundPackage != null) {
                        lastForeground[foregroundPackage] = nowMono
                    }
                }
                RuleType.WEBSITE -> {
                    if (idleBlocked) {
                        activities[id] = ActivityInput(active = false, idleBlocked = true)
                        details[id] = "idle blocked"
                        continue
                    }
                    val domain = rule.domain ?: ""
                    val lastSeen = webActive?.get(RuleMatcher.normalizeDomain(domain))
                    val fresh = lastSeen != null && (nowMono - lastSeen) < policy.websiteStaleSeconds
                    val inGrace = lastSeen != null && (nowMono - lastSeen) < policy.websiteGraceSeconds
                    val active = fresh && browserForeground && (inGrace || lastSeen != null)
                    activities[id] = ActivityInput(active = active, sessionType = "WEBSITE")
                    details[id] = if (active) "website $domain active in $foregroundPackage" else "website $domain inactive (browserForeground=$browserForeground, fresh=$fresh)"
                }
            }
        }
        return ResolvedActivity(activities, details, foregroundPackage, idleSeconds, idleBlocked)
    }
}
