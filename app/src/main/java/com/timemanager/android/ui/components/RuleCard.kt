package com.timemanager.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timemanager.android.domain.TimeUtils
import com.timemanager.android.ui.RuleUiState

@Composable
fun RuleCard(state: RuleUiState, onEdit: () -> Unit, onDelete: () -> Unit) {
    val progressColor = when (state.stateRole) {
        "bad" -> MaterialTheme.colorScheme.error
        "warn" -> MaterialTheme.colorScheme.tertiary
        "muted" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(state.rule.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.rule.type.name} · ${state.rule.target} · ${state.rule.action.name} · ${state.rule.mode.name}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.rule.schedule != null) {
                        Text("Schedule: ${state.rule.schedule.toJson()}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = progressColor)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${TimeUtils.formatDuration(state.usedSeconds)} used", style = MaterialTheme.typography.bodySmall)
                Text(
                    state.remainingToday?.let { "${TimeUtils.formatDuration(it)} left" } ?: "No daily limit",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AssistChip(onClick = {}, label = { Text(state.stateText) }, modifier = Modifier.padding(top = 4.dp))
            if (!state.rule.enabled) Text("Disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
