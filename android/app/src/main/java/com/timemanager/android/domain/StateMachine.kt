package com.timemanager.android.domain

import java.time.ZonedDateTime

data class TickInput(
    val rule: Rule,
    val todayUsedSeconds: Int,
    val sessionUsedSeconds: Int,
    val targetActiveNow: Boolean,
    val inGameSession: Boolean? = null, // null = n/a or unknown
    val detectorConfident: Boolean = true,
    val nowLocal: ZonedDateTime? = null,
    val storedState: RuleState = RuleState.NORMAL,
    val warnedThresholds: Set<Int> = emptySet(),
    val storedDay: String? = null,
    val todayDay: String? = null
)

data class Decision(
    val newState: RuleState,
    val warningsToFire: List<Int> = emptyList(),
    val actionToExecute: String? = null, // CLOSE_APP|BLOCK_WEBSITE|PREVENT_LAUNCH|NOTIFY_ONLY
    val reason: LimitReason = LimitReason.NONE,
    val userMessage: String = "",
    val remainingToday: Int? = null,
    val remainingSession: Int? = null
)

/**
 * Pure logic — direct port of app/core/enforcement/state_machine.py tick()
 */
object StateMachine {

    private fun remaining(limit: Int?, used: Int): Int? =
        limit?.let { maxOf(0, it - used) }

    fun tick(inp: TickInput): Decision {
        val rule = inp.rule
        val now = inp.nowLocal ?: ZonedDateTime.now()

        // Day rollover
        val (storedState, warned) = if (inp.storedDay != null && inp.todayDay != null && inp.storedDay != inp.todayDay) {
            RuleState.NORMAL to emptySet<Int>()
        } else {
            inp.storedState to inp.warnedThresholds
        }

        if (!rule.enabled) return Decision(newState = RuleState.DISABLED, reason = LimitReason.NONE)

        if (rule.schedule != null && !rule.schedule.isActive(now)) {
            if (storedState == RuleState.ENFORCED) {
                return Decision(newState = RuleState.ENFORCED, reason = LimitReason.SCHEDULE_BLOCKED)
            }
            return Decision(
                newState = RuleState.DISABLED,
                reason = LimitReason.SCHEDULE_BLOCKED,
                userMessage = "${rule.name} is outside its allowed schedule."
            )
        }

        val remainingToday = remaining(rule.dailyLimitSeconds, inp.todayUsedSeconds)
        val remainingSession = remaining(rule.sessionLimitSeconds, inp.sessionUsedSeconds)

        var reason = LimitReason.NONE
        if (rule.dailyLimitSeconds != null && inp.todayUsedSeconds >= rule.dailyLimitSeconds) {
            reason = LimitReason.DAILY_LIMIT_REACHED
        } else if (rule.sessionLimitSeconds != null && inp.sessionUsedSeconds >= rule.sessionLimitSeconds) {
            reason = LimitReason.SESSION_LIMIT_REACHED
        }

        if (reason != LimitReason.NONE) {
            return resolveLimit(rule, storedState, reason, remainingToday, remainingSession, inp)
        }

        // Warnings
        val fire = mutableListOf<Int>()
        for (horizon in listOfNotNull(remainingToday, remainingSession)) {
            for (w in rule.warningSeconds) {
                if (horizon <= w && w !in warned && w !in fire) fire.add(w)
            }
        }
        fire.sortDescending()
        if (fire.isNotEmpty()) {
            val wTxt = fire.joinToString(", ") { TimeUtils.formatDuration(it) }
            return Decision(
                newState = RuleState.WARNING,
                warningsToFire = fire,
                reason = LimitReason.NONE,
                userMessage = "${rule.name}: $wTxt remaining.",
                remainingToday = remainingToday,
                remainingSession = remainingSession
            )
        }

        // Stable states
        if (storedState in setOf(RuleState.WARNING, RuleState.NORMAL, RuleState.DISABLED)) {
            val state = if (warned.isNotEmpty()) RuleState.WARNING else RuleState.NORMAL
            return Decision(newState = state, reason = LimitReason.NONE, remainingToday = remainingToday, remainingSession = remainingSession)
        }
        if (storedState == RuleState.WAITING_FOR_SESSION_END) {
            return resolveLimit(rule, storedState, LimitReason.DAILY_LIMIT_REACHED, remainingToday, remainingSession, inp)
        }
        return Decision(newState = storedState, reason = LimitReason.NONE, remainingToday = remainingToday, remainingSession = remainingSession)
    }

    private fun resolveLimit(
        rule: Rule, storedState: RuleState, reason: LimitReason,
        remainingToday: Int?, remainingSession: Int?, inp: TickInput
    ): Decision {
        val limitWord = if (reason == LimitReason.DAILY_LIMIT_REACHED) "Daily" else "Session"
        val base = mapOf("remainingToday" to remainingToday, "remainingSession" to remainingSession, "reason" to reason)

        if (rule.action == Action.WARN_ONLY) {
            return Decision(
                newState = RuleState.ENFORCED, actionToExecute = "NOTIFY_ONLY",
                userMessage = "${rule.name}: ${limitWord.lowercase()} limit reached.",
                remainingToday = remainingToday, remainingSession = remainingSession, reason = reason
            )
        }
        if (rule.action == Action.WAIT_FOR_SESSION_END) {
            if (inp.inGameSession == true && inp.detectorConfident) {
                if (storedState == RuleState.WAITING_FOR_SESSION_END) {
                    return Decision(newState = RuleState.WAITING_FOR_SESSION_END, remainingToday = remainingToday, remainingSession = remainingSession, reason = reason)
                }
                return Decision(
                    newState = RuleState.WAITING_FOR_SESSION_END,
                    userMessage = "${rule.name}: ${limitWord.lowercase()} limit reached. Current match detected — it will be allowed to finish.",
                    remainingToday = remainingToday, remainingSession = remainingSession, reason = reason
                )
            }
            if (inp.inGameSession == null || !inp.detectorConfident) {
                return Decision(
                    newState = RuleState.WAITING_FOR_SESSION_END,
                    userMessage = "${rule.name}: game state could not be verified. Waiting for a safe session-end state.",
                    remainingToday = remainingToday, remainingSession = remainingSession, reason = reason
                )
            }
            // Confidently NOT in session -> enforce
            val action = if (rule.type == RuleType.WEBSITE) "BLOCK_WEBSITE" else "CLOSE_APP"
            val extra = if (storedState == RuleState.WAITING_FOR_SESSION_END) " Match ended. " else " "
            return Decision(
                newState = RuleState.ENFORCED, actionToExecute = action,
                userMessage = "${rule.name}:$extra${limitWord.lowercase()} limit reached. Enforced.",
                remainingToday = remainingToday, remainingSession = remainingSession, reason = reason
            )
        }
        val action = when {
            rule.type == RuleType.WEBSITE -> "BLOCK_WEBSITE"
            rule.action == Action.BLOCK && rule.type != RuleType.WEBSITE -> "PREVENT_LAUNCH"
            else -> "CLOSE_APP"
        }
        return Decision(
            newState = RuleState.ENFORCED, actionToExecute = action,
            userMessage = "${rule.name}: ${limitWord.lowercase()} limit reached.",
            remainingToday = remainingToday, remainingSession = remainingSession, reason = reason
        )
    }
}
