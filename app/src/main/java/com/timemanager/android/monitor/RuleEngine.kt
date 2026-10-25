package com.timemanager.android.monitor

import com.timemanager.android.data.AppRepository
import com.timemanager.android.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/**
 * Orchestration: resolve activity → tracker → state machine → persist + notify
 * Port of app/core/rules/engine.py RuleEngine
 */
data class ActivityInput(
    val active: Boolean,
    val sessionType: String = "FOREGROUND",
    val inGameSession: Boolean? = null,
    val detectorConfident: Boolean = true,
    val idleBlocked: Boolean = false
)

data class RuleOutcome(
    val rule: Rule,
    val decision: Decision,
    val todayUsedSeconds: Int,
    val sessionUsedSeconds: Int,
    val sessionId: Long?,
    val trackedActive: Boolean
)

class RuleEngine(
    private val repo: AppRepository,
    private val tracker: Tracker
) {
    suspend fun tick(activities: Map<Long, ActivityInput>): List<RuleOutcome> = withContext(Dispatchers.IO) {
        val nowLocal = ZonedDateTime.now()
        val today = TimeUtils.localDayStr(nowLocal)
        val rules = repo.listRules().filter { it.id != null }

        // Build effective activity map with schedule gating (matches desktop)
        val effective = mutableMapOf<Long, Activity>()
        for (rule in rules) {
            val id = rule.id!!
            val inp = activities[id] ?: ActivityInput(active = false)
            val countable = rule.enabled && inp.active && (rule.schedule == null || rule.schedule.isActive(nowLocal))
            effective[id] = Activity(active = countable, sessionType = inp.sessionType)
        }
        val infos = tracker.poll(effective)

        val outcomes = mutableListOf<RuleOutcome>()
        for (rule in rules) {
            val id = rule.id!!
            val inp = activities[id] ?: ActivityInput(active = false)
            val info = infos[id]
            val sessionUsed = info?.sessionSeconds ?: 0
            val sessionId = info?.sessionId
            val tracked = info?.trackedActive ?: false
            val todayUsed = tracker.todayTotal(id, today)

            val stored = repo.getEnforcementState(id)
            val (storedDay, storedState, warned) = stored ?: Triple(today, RuleState.NORMAL, emptySet())

            val decision = StateMachine.tick(
                TickInput(
                    rule = rule,
                    todayUsedSeconds = todayUsed,
                    sessionUsedSeconds = sessionUsed,
                    targetActiveNow = inp.active,
                    inGameSession = inp.inGameSession,
                    detectorConfident = inp.detectorConfident,
                    nowLocal = nowLocal,
                    storedState = storedState,
                    warnedThresholds = warned,
                    storedDay = storedDay,
                    todayDay = today
                )
            )
            repo.putEnforcementState(id, today, decision.newState, warned + decision.warningsToFire.toSet())

            // Audit log for enforcement
            if (decision.actionToExecute != null) {
                repo.logEnforcement(id, today, decision.actionToExecute, rule.mode.name, "EXECUTED", decision.detailForLog())
            }

            outcomes.add(RuleOutcome(rule, decision, todayUsed, sessionUsed, sessionId, tracked))
        }
        outcomes
    }

    private fun Decision.detailForLog(): String = userMessage
}
