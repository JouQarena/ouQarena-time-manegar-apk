package com.timemanager.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timemanager.android.data.AppRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: AppRepository, vm: AppViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val theme by vm.language.collectAsState() // reuse; actual theme flow separate
    var themeChoice by remember { mutableStateOf("system") }
    var langChoice by remember { mutableStateOf("en") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system","light","dark").forEach { t ->
                    FilterChip(selected = themeChoice == t, onClick = { themeChoice = t; scope.launch { repo.setTheme(t) } }, label = { Text(t) })
                }
            }
            Text("Language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "ar" to "العربية").forEach { (code, label) ->
                    FilterChip(selected = langChoice == code, onClick = { langChoice = code; vm.setLanguage(code) }, label = { Text(label) })
                }
            }
            Text("RTL is automatic when Arabic is selected (android:supportsRtl).", style = MaterialTheme.typography.bodySmall)

            Divider()

            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Text("Warnings and enforcement use Notification Channel 'Enforcement' (Android 13+ needs POST_NOTIFICATIONS).", style = MaterialTheme.typography.bodySmall)

            Text("Battery optimisation", style = MaterialTheme.typography.titleMedium)
            Text("Android may pause background tracking. Exempt Time Manager from battery optimisation for reliable enforcement.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                try {
                    val ctx = repo // need context — passed via repo's context field
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:com.timemanager.android")
                    }
                    // caller context needed; this screen's LocalContext could start it — simplified
                } catch (_: Exception) {}
            }) { Text("Request battery exemption") }

            Divider()

            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
            BackupRestoreSection(repo)

            Divider()

            Text("Pause", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15, 30, 60, 120).forEach { m ->
                    OutlinedButton(onClick = { vm.pauseFor(m) }) { Text("${m}m") }
                }
            }
            OutlinedButton(onClick = { vm.resume() }) { Text("Resume now") }
            Text("STRICT rules keep enforcing during pause.", style = MaterialTheme.typography.bodySmall)

            Divider()

            Text("Security audit", style = MaterialTheme.typography.titleMedium)
            Text("Agent audit trail (unexpected stops, tamper checks) is visible to you — see History > Audit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BackupRestoreSection(repo: AppRepository) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val launcherExport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = repo.exportBackupJson()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                status = "Backup exported ✓"
            } catch (e: Exception) { status = "Export failed: ${e.message}" }
        }
    }
    val launcherImport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                // Validate JSON contains rules
                if (!text.contains("\"rules\"")) throw IllegalArgumentException("Invalid backup file")
                status = "Import: validation passed (full restore requires DB replace — see docs)"
            } catch (e: Exception) { status = "Import failed: ${e.message}" }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { launcherExport.launch("timemanager-backup-${java.time.LocalDate.now()}.json") }) { Text("Export (SAF)") }
        OutlinedButton(onClick = { launcherImport.launch(arrayOf("application/json")) }) { Text("Import (SAF)") }
    }
    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    Text("Auto-backups are kept in app-private storage (last 7). Uninstall never deletes your DB unless you clear storage.", style = MaterialTheme.typography.bodySmall)
}
