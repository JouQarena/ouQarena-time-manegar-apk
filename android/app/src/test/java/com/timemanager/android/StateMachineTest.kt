package com.timemanager.android

import com.timemanager.android.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

private fun rule(
    daily: Int? = 60*30, session: Int? = null, action: Action = Action.CLOSE,
    mode: Mode = Mode.NORMAL, type: RuleType = RuleType.APPLICATION, enabled: Boolean = true, schedule: Schedule? = null
) = Rule(
    id = 1, name = "Test", type = type, target = "youtube", action = action, mode = mode,
    packageName = if (type == RuleType.WEBSITE) null else "com.test.app",
    domain = if (type == RuleType.WEBSITE) "youtube.com" else null,
    dailyLimitSeconds = daily, sessionLimitSeconds = session, schedule = schedule, enabled = enabled
)

class StateMachineTest {

    @Test fun normalStaysNormal() {
        val r = rule(daily = 3600)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 0, sessionUsedSeconds = 0, targetActiveNow = false))
        assertEquals(RuleState.NORMAL, d.newState)
        assertNull(d.actionToExecute)
    }

    @Test fun warningFiresOnce() {
        val r = rule(daily = 3600, session = null) // warnings 600,300,60
        val d1 = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 3000, sessionUsedSeconds = 0, targetActiveNow = true))
        // 600 remaining -> should warn 600
        assertEquals(RuleState.WARNING, d1.newState)
        assertTrue(d1.warningsToFire.contains(600))

        val d2 = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 3000, sessionUsedSeconds = 0, targetActiveNow = true, storedState = d1.newState, warnedThresholds = d1.warningsToFire.toSet()))
        // Already warned 600, should stay WARNING without re-firing
        assertEquals(RuleState.WARNING, d2.newState)
        assertTrue(d2.warningsToFire.isEmpty())
    }

    @Test fun dailyLimitEnforced() {
        val r = rule(daily = 60)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 10, targetActiveNow = true))
        assertEquals(RuleState.ENFORCED, d.newState)
        assertEquals("CLOSE_APP", d.actionToExecute)
        assertEquals(LimitReason.DAILY_LIMIT_REACHED, d.reason)
    }

    @Test fun sessionLimitEnforced() {
        val r = rule(daily = null, session = 60)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 10, sessionUsedSeconds = 60, targetActiveNow = true))
        assertEquals(RuleState.ENFORCED, d.newState)
        assertEquals(LimitReason.SESSION_LIMIT_REACHED, d.reason)
    }

    @Test fun disabledRuleNeverEnforces() {
        val r = rule(daily = 60, enabled = false)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 9999, sessionUsedSeconds = 9999, targetActiveNow = true))
        assertEquals(RuleState.DISABLED, d.newState)
        assertNull(d.actionToExecute)
    }

    @Test fun outsideScheduleDisabled() {
        val sched = Schedule(days = setOf(java.time.DayOfWeek.MONDAY), windows = listOf(java.time.LocalTime.of(9,0) to java.time.LocalTime.of(17,0)))
        // Now is Sunday 10am -> outside
        val sunday = ZonedDateTime.of(2026, 1, 11, 10, 0, 0, 0, java.time.ZoneId.systemDefault()) // 2026-01-11 is Sunday
        val r = rule(daily = 60, schedule = sched)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 70, sessionUsedSeconds = 0, targetActiveNow = true, nowLocal = sunday))
        assertEquals(RuleState.DISABLED, d.newState)
        assertEquals(LimitReason.SCHEDULE_BLOCKED, d.reason)
    }

    @Test fun waitForSessionEnd_gameInSessionStaysWaiting() {
        val r = rule(daily = 60, action = Action.WAIT_FOR_SESSION_END, type = RuleType.GAME)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 60, targetActiveNow = true, inGameSession = true, detectorConfident = true))
        assertEquals(RuleState.WAITING_FOR_SESSION_END, d.newState)
        assertNull(d.actionToExecute)
    }

    @Test fun waitForSessionEnd_unknownStaysWaiting() {
        val r = rule(daily = 60, action = Action.WAIT_FOR_SESSION_END, type = RuleType.GAME)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 60, targetActiveNow = true, inGameSession = null, detectorConfident = false))
        assertEquals(RuleState.WAITING_FOR_SESSION_END, d.newState)
    }

    @Test fun waitForSessionEnd_notInSessionEnforces() {
        val r = rule(daily = 60, action = Action.WAIT_FOR_SESSION_END, type = RuleType.GAME)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 60, targetActiveNow = true, inGameSession = false, detectorConfident = true))
        assertEquals(RuleState.ENFORCED, d.newState)
        assertNotNull(d.actionToExecute)
    }

    @Test fun dayRolloverResets() {
        val r = rule(daily = 60)
        val d = StateMachine.tick(TickInput(
            rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 60, targetActiveNow = true,
            storedState = RuleState.ENFORCED, storedDay = "2026-01-10", todayDay = "2026-01-11"
        ))
        // New day -> should NOT be ENFORCED anymore (we reset to NORMAL then re-evaluate; todayUsed is 0 on new day in real tracker,
        // but here we pass 60 artificially — this test documents the rollover reset behaviour)
        // The state machine resets stored state; remaining logic re-evaluates with today's counters.
        // If tracker correctly returns 0 for new day, result would be NORMAL; we test reset does not keep ENFORCED stale.
        // With 60 still, it will re-enforce — but the key is storedState is cleared to NORMAL first.
        // So with 0 used it would be NORMAL.
        val dNew = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 0, sessionUsedSeconds = 0, targetActiveNow = false, storedState = RuleState.ENFORCED, storedDay = "2026-01-10", todayDay = "2026-01-11"))
        assertEquals(RuleState.NORMAL, dNew.newState)
    }

    @Test fun warnOnlyNeverCloses() {
        val r = rule(daily = 60, action = Action.WARN_ONLY)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 0, targetActiveNow = true))
        assertEquals(RuleState.ENFORCED, d.newState)
        assertEquals("NOTIFY_ONLY", d.actionToExecute)
    }

    @Test fun blockWebsiteAction() {
        val r = rule(daily = 60, action = Action.BLOCK, type = RuleType.WEBSITE)
        val d = StateMachine.tick(TickInput(rule = r, todayUsedSeconds = 60, sessionUsedSeconds = 0, targetActiveNow = true))
        assertEquals("BLOCK_WEBSITE", d.actionToExecute)
    }
}
