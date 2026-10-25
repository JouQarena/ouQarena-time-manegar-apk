package com.timemanager.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timemanager.android.data.AppRepository
import com.timemanager.android.domain.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun RuleEditorScreen(repo: AppRepository, ruleId: Long?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RuleType.APPLICATION) }
    var action by remember { mutableStateOf(Action.BLOCK) }
    var mode by remember { mutableStateOf(Mode.NORMAL) }
    var dailyMin by remember { mutableStateOf("30") }
    var sessionMin by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Schedule editor state
    var scheduleEnabled by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("22:00") }

    // Load existing
    LaunchedEffect(ruleId) {
        if (ruleId != null && ruleId != -1L) {
            repo.getRule(ruleId)?.let { r ->
                name = r.name; target = r.target; packageName = r.packageName ?: ""; domain = r.domain ?: ""
                type = r.type; action = r.action; mode = r.mode
                dailyMin = r.dailyLimitSeconds?.let { (it/60).toString() } ?: ""
                sessionMin = r.sessionLimitSeconds?.let { (it/60).toString() } ?: ""
                enabled = r.enabled
                r.schedule?.let { s ->
                    scheduleEnabled = true
                    selectedDays = s.days ?: emptySet()
                    if (s.windows.isNotEmpty()) {
                        startTime = "%02d:%02d".format(s.windows[0].first.hour, s.windows[0].first.minute)
                        endTime = "%02d:%02d".format(s.windows[0].second.hour, s.windows[0].second.minute)
                    }
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (ruleId == null || ruleId == -1L) "New Rule" else "Edit Rule") }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Rule name") }, modifier = Modifier.fillMaxWidth())
            // Type
            Text("Rule type", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t; if (t == RuleType.WEBSITE) action = Action.BLOCK }, label = { Text(t.name) })
                }
            }
            OutlinedTextField(target, { target = it }, label = { Text("Display target (e.g. YouTube)") }, modifier = Modifier.fillMaxWidth())
            if (type != RuleType.WEBSITE) {
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Package name (e.g. com.google.android.youtube)") }, modifier = Modifier.fillMaxWidth())
                Text("Tip: Install the target app first — the picker can fill this.", style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain (e.g. youtube.com)") }, modifier = Modifier.fillMaxWidth())
            }
            Text("Action", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Action.BLOCK, Action.CLOSE, Action.WAIT_FOR_SESSION_END, Action.WARN_ONLY).forEach { a ->
                    FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a.name) })
                }
            }
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.name) })
                }
            }
            if (mode == Mode.STRICT) Text("STRICT: no grandfathering, harder pause, usage-floor enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

            OutlinedTextField(dailyMin, { dailyMin = it }, label = { Text("Daily limit (minutes, empty = none)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sessionMin, { sessionMin = it }, label = { Text("Session limit (minutes, empty = none)") }, modifier = Modifier.fillMaxWidth())

            // Schedule (weekly)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                Text("Schedule (days + time window)")
            }
            if (scheduleEnabled) {
                Text("Days (empty = every day):", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = d in selectedDays, onClick = {
                            selectedDays = if (d in selectedDays) selectedDays - d else selectedDays + d
                        }, label = { Text(d.name.take(3)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("Start HH:MM") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("End HH:MM (may cross midnight)") }, modifier = Modifier.weight(1f))
                }
                Text("Example overnight: 22:00–02:00 covers 22:00–23:59 today + 00:00–02:00 tomorrow.", style = MaterialTheme.typography.bodySmall)
            }

            // One-time schedule concept (date-bounded) — stored as daily limit reset + enabled toggle helper
            Text("One-time schedule: keep rule enabled only for a date range by manually toggling 'Enabled' or use the schedule above with specific days.", style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = enabled, onCheckedChange = { enabled = it }); Text("Enabled")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = {
                scope.launch {
                    try {
                        val dailySec = dailyMin.toIntOrNull()?.let { it * 60 }
                        val sessionSec = sessionMin.toIntOrNull()?.let { it * 60 }
                        if (dailySec == null && sessionSec == null) { error = "At least one limit required."; return@launch }
                        val sched = if (scheduleEnabled) {
                            val s = LocalTime.parse(startTime); val e = LocalTime.parse(endTime)
                            Schedule(days = if (selectedDays.isEmpty()) null else selectedDays, windows = listOf(s to e))
                        } else null
                        val pkg = packageName.ifBlank { null }
                        val dom = domain.ifBlank { null }?.let { RuleMatcher.normalizeDomain(it) }
                        val rule = Rule(
                            id = if (ruleId == null || ruleId == -1L) null else ruleId,
                            name = name.trim(), type = type, target = target.trim().ifEmpty { name.trim() },
                            action = action, mode = mode, packageName = pkg, domain = dom,
                            dailyLimitSeconds = dailySec, sessionLimitSeconds = sessionSec,
                            schedule = sched, enabled = enabled
                        )
                        rule.validate()
                        if (rule.id == null) repo.addRule(rule) else repo.updateRule(rule)
                        onDone()
                    } catch (e: Exception) { error = e.message }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save rule") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun FlowRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, content: @Composable () -> Unit) {
    // Simple wrapper for FlowRow (Compose 1.6+ has FlowRow; fallback to Row with wrap)
    Row(modifier, horizontalArrangement = horizontalArrangement) { content() }
}
