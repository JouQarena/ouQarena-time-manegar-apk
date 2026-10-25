package com.timemanager.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timemanager.android.data.AppRepository
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(repo: AppRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<com.timemanager.android.data.entity.DailyUsageEntity>>(emptyList()) }
    var audit by remember { mutableStateOf<List<com.timemanager.android.data.entity.AgentAuditEntity>>(emptyList()) }
    var logs by remember { mutableStateOf<List<com.timemanager.android.data.entity.EnforcementLogEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        history = repo.db.dailyDao().allHistory().take(30)
        audit = repo.db.agentAuditDao().recent()
        logs = repo.db.enforcementLogDao().recent()
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Study History & Audit") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Study history (per day)", style = MaterialTheme.typography.titleSmall)
                    Text("Tracking is exact: monotonic durations split across midnight; unflushed time is never lost.", style = MaterialTheme.typography.bodySmall)
                }}
            }
            items(history) { h ->
                Card { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${h.day}"); Text("${h.totalSeconds/60}m (${h.totalSeconds}s) • rule ${h.ruleId}")
                }}
            }
            item { Divider(); Text("Enforcement log (audited)", style = MaterialTheme.typography.titleSmall) }
            items(logs.take(20)) { e ->
                Card { Column(Modifier.padding(10.dp)) {
                    Text("${e.day} rule ${e.ruleId} ${e.action} ${e.outcome}", style = MaterialTheme.typography.bodySmall)
                    e.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }}
            }
            item { Divider(); Text("Agent audit (anti-bypass)", style = MaterialTheme.typography.titleSmall) }
            items(audit.take(20)) { a ->
                Card { Column(Modifier.padding(10.dp)) {
                    Text("${a.kind} [${a.severity}] ${a.recordedAt.take(19)}", style = MaterialTheme.typography.bodySmall)
                    a.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }}
            }
        }
    }
}
