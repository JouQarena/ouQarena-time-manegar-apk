package com.timemanager.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SchedulesScreen(vm: AppViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.dashboard.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Schedules") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("Weekly & One-time Schedules", style = MaterialTheme.typography.titleMedium)
                        Text("• Weekly: set days (Mon–Sun) and time window (e.g. 08:00–22:00, overnight 22:00–02:00 handled).\n• One-time: create a rule and toggle Enabled for the date you need; or keep a single weekly window for the exam period.", style = MaterialTheme.typography.bodySmall)
                        Text("Overlapping schedules: if any window covers 'now', the rule is active. Device reboot and time-zone changes are handled via BOOT_COMPLETED + TIMEZONE_CHANGED receivers that reschedule midnight resets.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(state.rules) { rs ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(rs.rule.name, style = MaterialTheme.typography.titleSmall)
                        Text(rs.rule.schedule?.toJson() ?: "Always active", style = MaterialTheme.typography.bodySmall)
                        Text("State: ${rs.stateText} · ${rs.remainingToday?.let { com.timemanager.android.domain.TimeUtils.formatDuration(it) + " left"} ?: "No daily cap"}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onEdit(rs.rule.id ?: -1) }) { Text("Edit rule →") }
                    }
                }
            }
        }
    }
}
