package com.shutterly.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shutterly.app.ui.theme.ShutterlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShutterlyTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = viewModel(factory = RecordViewModel.Factory),
                onAdd = { nav.navigate("add") },
                onStats = { nav.navigate("stats") }
            )
        }
        composable("add") {
            AddRecordScreen(
                vm = viewModel(factory = RecordViewModel.Factory),
                onDone = { nav.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(
                vm = viewModel(factory = RecordViewModel.Factory),
                onBack = { nav.popBackStack() }
            )
        }
    }
}
