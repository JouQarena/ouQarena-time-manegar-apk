package com.timemanager.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.timemanager.android.data.AppRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppRepository(applicationContext)
        setContent {
            val nav = rememberNavController()
            val vm: AppViewModel = viewModel(factory = ViewModelFactory(repo))
            // Apply language RTL awareness
            TimeManagerTheme {
                NavHost(navController = nav, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(vm, onEdit = { id -> nav.navigate("editor/$id") }, onAdd = { nav.navigate("editor/-1") }, onNav = { nav.navigate(it) })
                    }
                    composable("editor/{id}") { entry ->
                        val id = entry.arguments?.getString("id")?.toLongOrNull()
                        RuleEditorScreen(repo, id, onDone = { nav.popBackStack(); vm.refresh() })
                    }
                    composable("settings") { SettingsScreen(repo, vm, onBack = { nav.popBackStack() }) }
                    composable("permissions") { PermissionsScreen(onBack = { nav.popBackStack() }) }
                    composable("pdf") { PdfLibraryScreen(repo, onBack = { nav.popBackStack() }) }
                    composable("schedules") {
                        SchedulesScreen(vm, onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("editor/$id") })
                    }
                    composable("stats") { StatsScreen(repo, onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
