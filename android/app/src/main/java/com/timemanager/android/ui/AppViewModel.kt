package com.timemanager.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemanager.android.data.AppRepository
import com.timemanager.android.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class RuleUiState(
    val rule: Rule,
    val usedSeconds: Int,
    val sessionSeconds: Int,
    val state: RuleState,
    val remainingToday: Int?,
    val remainingSession: Int?,
    val progress: Float,
    val stateText: String,
    val stateRole: String
)

data class DashboardUiState(
    val rules: List<RuleUiState> = emptyList(),
    val studyState: StudyState = StudyState.NOT_STUDYING,
    val isPaused: Boolean = false,
    val pauseRemaining: String = "",
    val loading: Boolean = true
)

class AppViewModel(private val repo: AppRepository) : ViewModel() {

    private val _dashboard = MutableStateFlow(DashboardUiState())
    val dashboard: StateFlow<DashboardUiState> = _dashboard

    private val _rulesRaw = MutableStateFlow<List<Rule>>(emptyList())
    val language = repo.languageFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _dashboard.value = _dashboard.value.copy(loading = true)
            val rules = repo.listRules()
            _rulesRaw.value = rules
            val today = TimeUtils.localDayStr()
            val uiRules = rules.map { rule ->
                val id = rule.id ?: 0L
                val used = repo.getDaily(id, today)
                // enforcement state
                val stateInfo = repo.getEnforcementState(id)
                val state = stateInfo?.second ?: RuleState.NORMAL
                val remainingToday = rule.dailyLimitSeconds?.let { maxOf(0, it - used) }
                val limit = rule.dailyLimitSeconds ?: rule.sessionLimitSeconds ?: 1
                val progress = (used.toFloat() / limit).coerceIn(0f, 1f)
                RuleUiState(rule, used, 0, state, remainingToday, null, progress, stateText(state, rule), stateRole(state, rule))
            }
            // Pause state
            val pauseUntil = try { repo.getPauseUntil() } catch (_: Exception) { null }
            val paused = pauseUntil != null && try { java.time.Instant.parse(pauseUntil).isAfter(java.time.Instant.now()) } catch (_: Exception) { false }
            _dashboard.value = DashboardUiState(
                rules = uiRules,
                isPaused = paused,
                pauseRemaining = if (paused) pauseUntil ?: "" else "",
                studyState = if (paused) StudyState.NOT_STUDYING else deriveStudyState(uiRules),
                loading = false
            )
        }
    }

    fun deleteRule(id: Long) { viewModelScope.launch { repo.deleteRule(id); refresh() } }
    fun setLanguage(code: String) { viewModelScope.launch { repo.setLanguage(code); refresh() } }
    fun setTheme(code: String) { viewModelScope.launch { repo.setTheme(code) } }
    fun pauseFor(minutes: Int) {
        viewModelScope.launch {
            val until = InstantNow.plusSeconds(minutes * 60L).toString()
            repo.setPauseUntil(until); refresh()
        }
    }
    fun resume() { viewModelScope.launch { repo.setPauseUntil(null); refresh() } }

    private fun deriveStudyState(rules: List<RuleUiState>): StudyState {
        if (rules.isEmpty()) return StudyState.NOT_STUDYING
        val anyEnforced = rules.any { it.state == RuleState.ENFORCED || it.state == RuleState.WAITING_FOR_SESSION_END }
        return if (anyEnforced) StudyState.ACTIVE_STUDYING else StudyState.NOT_STUDYING
    }

    private fun stateText(s: RuleState, r: Rule): String = when {
        !r.enabled -> "Off"
        s == RuleState.DISABLED -> "Outside schedule"
        s == RuleState.ENFORCED -> when (r.action) {
            Action.WARN_ONLY -> "Limit warned"; Action.WAIT_FOR_SESSION_END -> "After match"; else -> "Blocked"
        }
        s == RuleState.WARNING -> "Warning"
        s == RuleState.WAITING_FOR_SESSION_END -> "Waiting for session end"
        s == RuleState.NORMAL -> "Ready"
        else -> s.name
    }
    private fun stateRole(s: RuleState, r: Rule): String = when (s) {
        RuleState.ENFORCED -> "bad"; RuleState.WARNING, RuleState.WAITING_FOR_SESSION_END -> "warn"
        RuleState.DISABLED -> "muted"; else -> "ok"
    }

    companion object {
        private val InstantNow get() = java.time.Instant.now()
    }
}

class ViewModelFactory(private val repo: AppRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
}
