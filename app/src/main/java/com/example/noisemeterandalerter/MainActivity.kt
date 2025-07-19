package com.example.noisemeterandalerter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.noisemeterandalerter.ui.theme.NoiseMeterAndAlerterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoiseMeterAndAlerterTheme {
                MainScreen()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object NoiseMeter : Screen("noise_meter", "Gürültü Ölçer")
    object History : Screen("history", "Geçmiş")
    object Settings : Screen("settings", "Ayarlar")
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.NoiseMeter.route,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            composable(Screen.NoiseMeter.route) { NoiseMeterScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val items = listOf(Screen.NoiseMeter, Screen.History, Screen.Settings)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                label = { Text(screen.title) },
                icon = { /* İkon eklenebilir */ }
            )
        }
    }
}

@Composable
fun NoiseMeterScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Gürültü Ölçer Ekranı")
    }
}

@Composable
fun HistoryScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Geçmiş/Grafik Ekranı")
    }
}

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Ayarlar Ekranı")
    }
}

