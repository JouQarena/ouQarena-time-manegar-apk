package com.timemanager.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timemanager.android.domain.*
import com.timemanager.android.ui.components.RuleCard

@Composable
fun DashboardScreen(vm: AppViewModel, onEdit: (Long) -> Unit, onAdd: () -> Unit, onNav: (String) -> Unit) {
    val state by vm.dashboard.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Manager") },
                actions = {
                    IconButton(onClick = { onNav("settings") }) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add rule") }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { StatusBanner(state) }
            item { PermissionsBanner(onNav) }
            if (state.rules.isEmpty() && !state.loading) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No rules yet", style = MaterialTheme.typography.titleMedium)
                            Text("Create a rule to start tracking — e.g. block YouTube after 30 min.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onAdd) { Text("Create first rule") }
                        }
                    }
                }
            }
            items(state.rules, key = { it.rule.id ?: it.rule.name.hashCode().toLong() }) { rs ->
                RuleCard(rs, onEdit = { onEdit(rs.rule.id ?: -1) }, onDelete = { vm.deleteRule(rs.rule.id ?: -1) })
            }
            item { StudyModeCard(state) }
            item { QuickActions(onNav, vm) }
        }
    }
}

@Composable
private fun StatusBanner(s: DashboardUiState) {
    val color = when (s.studyState) {
        StudyState.ACTIVE_STUDYING -> MaterialTheme.colorScheme.primaryContainer
        StudyState.PASSIVE_READING -> MaterialTheme.colorScheme.secondaryContainer
        StudyState.AWAY -> MaterialTheme.colorScheme.errorContainer
        StudyState.NOT_STUDYING -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(StudyModeResolver.label(s.studyState), style = MaterialTheme.typography.titleSmall)
                Text(if (s.isPaused) "Paused — ${s.pauseRemaining}" else "${s.rules.size} rules", style = MaterialTheme.typography.bodySmall)
            }
            if (s.isPaused) {
                // resume button via vm would need callback; simplified
            }
        }
    }
}

@Composable
private fun PermissionsBanner(onNav: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text("Permissions & enforcement", style = MaterialTheme.typography.titleSmall)
            Text("Tap to review Usage Access, Notifications, Overlay and Battery settings.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onNav("permissions") }) { Text("Review permissions →") }
        }
    }
}

@Composable
private fun StudyModeCard(s: DashboardUiState) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Study Mode", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("• Active Studying — foreground app counted\n• Passive Reading — PDF reader counted only if enabled\n• Away — idle/screen-off, not counted\n• Not Studying — paused or outside schedule", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickActions(onNav: (String) -> Unit, vm: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onNav("schedules") }) { Text("Schedules") }
        OutlinedButton(onClick = { onNav("pdf") }) { Text("PDF Library") }
        OutlinedButton(onClick = { onNav("stats") }) { Text("History") }
    }
}
