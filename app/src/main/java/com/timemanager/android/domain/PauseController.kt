package com.timemanager.android.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Port of app/core/pause.py
 * Deadline stored as wall-clock ISO string in DataStore (survives restart).
 */
data class PauseState(
    val active: Boolean,
    val untilIso: String = "",
    val remainingSeconds: Int = 0,
    val exemptRules: List<String> = emptyList() // STRICT rules still enforced
) {
    val minutesLeft: Int get() = maxOf(0, (remainingSeconds + 59) / 60)
}

class PauseController(
    private val nowProvider: () -> Instant = { Instant.now() },
    private val strictRuleNamesProvider: () -> List<String> = { emptyList() }
) {
    companion object {
        const val SETTING_KEY = "pause_until"
        const val MIN_PAUSE_MINUTES = 1
        const val MAX_PAUSE_MINUTES = 8 * 60
    }

    fun isValidMinutes(minutes: Int): Boolean = minutes in MIN_PAUSE_MINUTES..MAX_PAUSE_MINUTES

    fun pauseForMinutes(minutes: Int): String {
        require(isValidMinutes(minutes)) { "Pause must be $MIN_PAUSE_MINUTES..$MAX_PAUSE_MINUTES minutes." }
        val until = nowProvider().plusSeconds(minutes * 60L)
        return until.toString()
    }

    fun resolve(untilIso: String?): PauseState {
        if (untilIso.isNullOrBlank()) return PauseState(active = false)
        return try {
            val until = Instant.parse(untilIso)
            val remaining = (until.epochSecond - nowProvider().epochSecond).toInt()
            if (remaining <= 0) PauseState(active = false)
            else PauseState(
                active = true,
                untilIso = untilIso,
                remainingSeconds = remaining,
                exemptRules = strictRuleNamesProvider()
            )
        } catch (e: Exception) {
            PauseState(active = false)
        }
    }

    fun humanRemaining(state: PauseState): String = when {
        !state.active -> "Not paused"
        else -> TimeUtils.formatDuration(state.remainingSeconds) + " left"
    }
}
