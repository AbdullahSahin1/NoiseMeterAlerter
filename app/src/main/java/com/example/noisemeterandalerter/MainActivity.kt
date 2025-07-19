package com.example.noisemeterandalerter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.noisemeterandalerter.ui.theme.NoiseMeterAndAlerterTheme
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

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
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isMeasuring by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted) isMeasuring = true
        }
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isMeasuring && hasMicPermission) {
                Text(text = "Mikrofon aktif")
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = { isMeasuring = false }) {
                    Text("Durdur")
                }
            } else {
                Text(text = "Gürültü Ölçer Ekranı")
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = {
                    if (hasMicPermission) {
                        isMeasuring = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text("Başlat")
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    var history by remember {
        mutableStateOf(listOf(60, 65, 70, 80, 75, 72, 68, 66, 70, 74, 78, 72, 69))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Ses Seviyesi Geçmişi", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(16.dp))
            // Grafik Alanı
            Canvas(modifier = Modifier
                .size(width = 300.dp, height = 150.dp)
                .padding(8.dp)) {
                if (history.isNotEmpty()) {
                    val maxVal = (history.maxOrNull() ?: 100).toFloat()
                    val minVal = (history.minOrNull() ?: 0).toFloat()
                    val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                    val scaleY = if (maxVal - minVal == 0f) 1f else size.height / (maxVal - minVal)
                    val path = Path()
                    history.forEachIndexed { i, value ->
                        val x = i * stepX
                        val y = size.height - (value - minVal) * scaleY
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = Color(0xFF1976D2), style = Stroke(width = 4f))
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Button(onClick = { history = emptyList() }) {
                Text("Geçmişi Temizle")
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var threshold by remember { mutableStateOf(70f) } // dB
    var selectedAlert by remember { mutableStateOf("Görsel Uyarı") }
    val alertTypes = listOf("Görsel Uyarı", "Titreşim", "Bildirim")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Gürültü Eşiği: ${threshold.toInt()} dB", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 50f..100f,
                steps = 5,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.size(24.dp))
            Text(text = "Uyarı Tipi", style = MaterialTheme.typography.titleMedium)
            alertTypes.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedAlert == type,
                        onClick = { selectedAlert = type }
                    )
                    Text(text = type)
                }
            }
            Spacer(modifier = Modifier.size(24.dp))
            Button(onClick = { /* Ayarları kaydetme işlemi buraya eklenebilir */ }) {
                Text("Kaydet")
            }
        }
    }
}

