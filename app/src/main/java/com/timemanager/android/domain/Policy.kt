package com.timemanager.android.domain

/**
 * Port of app/core/enforcement/policy.py — pure decision about which packages may be interrupted.
 * Android note: "kill" is replaced by overlay+home navigation under normal permissions; Device Owner
 * can use setPackagesSuspended(). This policy decides which packages *should* be interrupted.
 */

// Protected packages the agent must never block (system-critical)
val PROTECTED_PACKAGES: Set<String> = setOf(
    "android", "com.android.systemui", "com.android.launcher3",
    "com.google.android.gms", "com.android.phone", "com.android.settings",
    "com.timemanager.android" // self
)

fun isProtected(packageName: String, extra: Set<String> = emptySet()): String? {
    val p = packageName.lowercase()
    if (p in PROTECTED_PACKAGES || p in extra.map { it.lowercase() }) return "protected package ($p)"
    if (p == "com.timemanager.android") return "agent package"
    return null
}

data class KillDecision(
    val packagesToBlock: List<String> = emptyList(),
    val grandfathered: List<String> = emptyList(),
    val skipped: List<Triple<String, String, String>> = emptyList(), // pkg, reason
    val reason: String = ""
) {
    val hasWork: Boolean get() = packagesToBlock.isNotEmpty()
    fun detail(): String {
        val bits = mutableListOf<String>()
        if (packagesToBlock.isNotEmpty()) bits.add("packages=" + packagesToBlock.joinToString(","))
        if (grandfathered.isNotEmpty()) bits.add("grandfathered=" + grandfathered.joinToString(","))
        if (skipped.isNotEmpty()) bits.add("skipped=" + skipped.joinToString(",") { (a,_,r) -> "$a($r)" })
        return bits.joinToString("; ")
    }
}

/**
 * Remembers which packages were already running when enforcement began.
 * Persisted per rule/day in DataStore so restart doesn't misclassify.
 */
interface GrandpaStore {
    fun load(ruleId: Long): Set<String>?
    fun save(ruleId: Long, packages: Set<String>)
    fun clear(ruleId: Long)
}

class KillPolicy(
    private val extraProtected: Set<String> = emptySet(),
    private val selfPackage: String = "com.timemanager.android"
) {
    fun decide(
        rule: Rule,
        action: String?,
        matched: Set<String>, // packages currently detected as foreground/relevant
        grandfathered: Set<String> = emptySet(),
        firstEnforcement: Boolean = false
    ): KillDecision {
        if (action !in setOf("CLOSE_APP", "PREVENT_LAUNCH")) {
            return KillDecision(reason = "action $action needs no package work")
        }
        val live = mutableSetOf<String>()
        val skipped = mutableListOf<Triple<String,String,String>>()
        for (pkg in matched) {
            val why = isProtected(pkg, extraProtected) ?: if (pkg == selfPackage) "agent package" else null
            if (why != null) skipped.add(Triple(pkg, pkg, why))
            else live.add(pkg)
        }

        val exempting = rule.mode == Mode.NORMAL && action == "PREVENT_LAUNCH"
        if (exempting && firstEnforcement) {
            return KillDecision(
                packagesToBlock = emptyList(),
                grandfathered = live.sorted(),
                skipped = skipped,
                reason = "NORMAL/BLOCK: existing instances grandfathered"
            )
        }
        return if (exempting) {
            val exempt = grandfathered.intersect(live)
            val doomed = (live - exempt).sorted()
            KillDecision(
                packagesToBlock = doomed,
                grandfathered = exempt.sorted(),
                skipped = skipped,
                reason = "${rule.mode}/${rule.action}: blocking ${doomed.size} of ${live.size + skipped.size} matched"
            )
        } else {
            KillDecision(
                packagesToBlock = live.sorted(),
                grandfathered = emptyList(),
                skipped = skipped,
                reason = "${rule.mode}/${rule.action}: blocking ${live.size} of ${live.size + skipped.size} matched"
            )
        }
    }
}
