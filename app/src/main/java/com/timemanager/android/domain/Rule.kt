package com.timemanager.android.domain

import kotlinx.serialization.json.Json

data class Rule(
    val id: Long? = null,
    val name: String,
    val type: RuleType,
    val target: String, // display target as typed
    val action: Action,
    val mode: Mode = Mode.NORMAL,
    val packageName: String? = null, // Android: replaces executable (e.g. com.discord)
    val extraPackages: List<String> = emptyList(),
    val domain: String? = null, // website
    val dailyLimitSeconds: Int? = null,
    val sessionLimitSeconds: Int? = null,
    val warningSeconds: List<Int> = listOf(600, 300, 60),
    val schedule: Schedule? = null, // null = always active
    val enabled: Boolean = true,
    // Compatibility with desktop model
    val executable: String? = null, // deprecated: mapped to packageName on migration
    val extraExecutables: List<String> = emptyList()
) {
    init { validate() }

    fun validate() {
        require(name.isNotBlank()) { "Rule name must not be empty." }
        require(name.length <= 120) { "Rule name too long (max 120 chars)." }

        when (type) {
            RuleType.APPLICATION, RuleType.GAME -> {
                val pkg = packageName ?: executable?.let { exeToPackage(it) }
                require(!pkg.isNullOrBlank()) { "${type.name} rules require a packageName/executable." }
                require(domain == null) { "Application/game rules must not carry a domain." }
                if (type == RuleType.GAME && action !in setOf(Action.WAIT_FOR_SESSION_END, Action.CLOSE, Action.BLOCK, Action.WARN_ONLY)) {
                    throw IllegalArgumentException("Invalid action for game rule.")
                }
            }
            RuleType.WEBSITE -> {
                require(!domain.isNullOrBlank()) { "Website rules require a domain." }
                require(packageName == null && executable == null) { "Website rules must not carry a package/executable." }
                require(action != Action.CLOSE) { "Website rules cannot use CLOSE (use BLOCK)." }
            }
        }
        for ((label, v) in listOf("daily_limit_seconds" to dailyLimitSeconds, "session_limit_seconds" to sessionLimitSeconds)) {
            if (v != null) require(v in 1..86400) { "$label must be 1..86400 seconds or None." }
        }
        require(dailyLimitSeconds != null || sessionLimitSeconds != null) { "At least one of daily/session limit must be set." }
        for (w in warningSeconds) require(w in 1..86400) { "warning_seconds entries must be 1..86400." }
    }

    /** Desktop exe name -> Android package heuristic (best-effort). Real mapping happens in migration UI picker. */
    private fun exeToPackage(exe: String): String {
        val lower = exe.lowercase().removeSuffix(".exe")
        return when (lower) {
            "chrome" -> "com.android.chrome"
            "msedge" -> "com.microsoft.emmx"
            "firefox" -> "org.mozilla.firefox"
            "discord" -> "com.discord"
            "telegram" -> "org.telegram.messenger"
            "youtube" -> "com.google.android.youtube"
            "tiktok" -> "com.zhiliaoapp.musically"
            "instagram" -> "com.instagram.android"
            else -> lower // keep as-is; UI will prompt correction
        }
    }

    /** Normalized target packages (for matching UsageStats). */
    val packages: List<String>
        get() = if (type == RuleType.WEBSITE) emptyList()
        else listOfNotNull(packageName ?: executable?.let { exeToPackage(it) }) + extraPackages + extraExecutables.map { exeToPackage(it) }

    fun toScheduleJson(): String? = schedule?.toJson()

    companion object {
        fun fromDbRow(
            id: Long?, name: String, type: String, target: String, action: String, mode: String,
            packageName: String?, extraPackagesJson: String?, domain: String?,
            dailyLimit: Int?, sessionLimit: Int?, warningJson: String?,
            scheduleJson: String?, enabled: Int
        ): Rule {
            val extra = try {
                extraPackagesJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            val warnings = try {
                warningJson?.let { Json.decodeFromString<List<Int>>(it) } ?: listOf(600, 300, 60)
            } catch (_: Exception) { listOf(600, 300, 60) }
            val sched = scheduleJson?.let { Schedule.parse(it) }
            return Rule(
                id = id, name = name, type = RuleType.valueOf(type), target = target,
                action = Action.valueOf(action), mode = Mode.valueOf(mode),
                packageName = packageName, extraPackages = extra, domain = domain?.lowercase(),
                dailyLimitSeconds = dailyLimit, sessionLimitSeconds = sessionLimit,
                warningSeconds = warnings.distinct().sortedDescending(),
                schedule = sched, enabled = enabled != 0
            )
        }
    }
}

/** Matcher helpers — port of app/core/rules/matcher.py */
object RuleMatcher {
    fun normalizeDomain(raw: String): String {
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        d = d.substringBefore("/").substringBefore(":").substringBefore("?")
        return d
    }
    fun normalizePackage(raw: String): String = raw.trim().lowercase()

    fun domainMatches(ruleDomain: String, visitedDomain: String): Boolean {
        val r = normalizeDomain(ruleDomain)
        val v = normalizeDomain(visitedDomain)
        return v == r || v.endsWith(".$r")
    }

    /** Package match: exact (Android packages are unique). */
    fun packageMatches(rulePackages: List<String>, foregroundPackage: String): Boolean {
        val fg = normalizePackage(foregroundPackage)
        return rulePackages.any { normalizePackage(it) == fg }
    }
}
