package com.timemanager.android.domain

/**
 * Study Mode — Android UX layer that maps the generic prompt's
 * "Active Studying / Passive Reading / Away / Not Studying" onto the
 * existing desktop semantics:
 * - Active Studying: foreground counting enabled, enforcement armed
 * - Passive Reading: foreground counting with idle gate active (PDF reader, etc.)
 * - Away: idle detected or screen off — tracking paused automatically
 * - Not Studying: global pause or no schedule active — tracking paused
 *
 * This is a presentation/state concept; accounting still flows through Tracker + StateMachine.
 * StudyMode is driven by ActivityResolver + foreground + idle + pause signals.
 */
enum class StudyState {
    ACTIVE_STUDYING,   // rule active & in foreground, counting
    PASSIVE_READING,   // rule's allowed app is foreground but no touch for grace window
    AWAY,              // idle timeout or screen off
    NOT_STUDYING       // paused by user or outside schedule
}

data class StudyModeSnapshot(
    val state: StudyState,
    val activeRuleIds: List<Long> = emptyList(),
    val reason: String = "",
    val idleSeconds: Double? = null,
    val foregroundPackage: String? = null
)

object StudyModeResolver {
    /**
     * Derive StudyState from platform signals.
     * This is deliberately separate from enforcement state — it is for UX.
     */
    fun resolve(
        isPaused: Boolean,
        isOutsideSchedule: Boolean,
        isIdle: Boolean,
        isPassiveReading: Boolean, // e.g. PDF reader foreground + no input for readingGraceSeconds
        anyRuleActive: Boolean
    ): StudyState = when {
        isPaused || isOutsideSchedule -> StudyState.NOT_STUDYING
        isIdle -> StudyState.AWAY
        isPassiveReading -> StudyState.PASSIVE_READING
        anyRuleActive -> StudyState.ACTIVE_STUDYING
        else -> StudyState.NOT_STUDYING
    }

    fun label(state: StudyState): String = when (state) {
        StudyState.ACTIVE_STUDYING -> "Active Studying"
        StudyState.PASSIVE_READING -> "Passive Reading"
        StudyState.AWAY -> "Away"
        StudyState.NOT_STUDYING -> "Not Studying"
    }

    fun shouldCountTime(state: StudyState, allowPassiveReadingAsStudy: Boolean = false): Boolean = when (state) {
        StudyState.ACTIVE_STUDYING -> true
        StudyState.PASSIVE_READING -> allowPassiveReadingAsStudy // user setting; default false keeps parity with desktop's "foreground only"
        StudyState.AWAY, StudyState.NOT_STUDYING -> false
    }
}
