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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.widget.Toast
import androidx.compose.ui.graphics.nativeCanvas

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
    var history by remember { mutableStateOf(listOf<Int>()) }
    var threshold by remember { mutableStateOf(70f) } // dB
    var alertType by remember { mutableStateOf("Görsel Uyarı") }
    val alertTypes = listOf("Görsel Uyarı", "Titreşim", "Bildirim")
    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.NoiseMeter.route,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            composable(Screen.NoiseMeter.route) {
                NoiseMeterScreen(
                    onNewDecibel = { db ->
                        if (db > 0) history = (history + db.toInt()).takeLast(100)
                    },
                    threshold = threshold,
                    alertType = alertType
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(history = history, onClear = { history = emptyList() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    threshold = threshold,
                    onThresholdChange = { threshold = it },
                    alertType = alertType,
                    onAlertTypeChange = { alertType = it },
                    alertTypes = alertTypes
                )
            }
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
fun NoiseMeterScreen(
    onNewDecibel: (Float) -> Unit,
    threshold: Float,
    alertType: String
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isMeasuring by remember { mutableStateOf(false) }
    var decibel by remember { mutableStateOf(0f) }
    var showVisualAlert by remember { mutableStateOf(false) }
    var lastAlertTime by remember { mutableStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted) isMeasuring = true
        }
    )

    // Uyarı fonksiyonları
    fun triggerVibration() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
    fun triggerNotification() {
        val channelId = "noise_alert_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Gürültü Uyarıları", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Gürültü Uyarısı")
            .setContentText("Gürültü seviyesi eşiği aştı! (${decibel.toInt()} dB)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(1, notification)
    }

    // Ölçüm başladığında AudioRecord ile dB ölçümü başlat
    LaunchedEffect(isMeasuring, hasMicPermission) {
        if (isMeasuring && hasMicPermission) {
            val sampleRate = 44100
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            val buffer = ShortArray(bufferSize)
            audioRecord.startRecording()
            try {
                while (isMeasuring && hasMicPermission && coroutineContext.isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = Math.sqrt(sum / read)
                        val db = if (rms > 0) 20 * Math.log10(rms / 32768.0) + 90 else 0.0
                        decibel = db.toFloat().coerceAtLeast(0f)
                        onNewDecibel(decibel)
                        // Eşik aşımı kontrolü (her 2 saniyede bir uyarı)
                        if (decibel > threshold && System.currentTimeMillis() - lastAlertTime > 2000) {
                            when (alertType) {
                                "Görsel Uyarı" -> showVisualAlert = true
                                "Titreşim" -> triggerVibration()
                                "Bildirim" -> triggerNotification()
                            }
                            lastAlertTime = System.currentTimeMillis()
                        } else if (decibel <= threshold) {
                            showVisualAlert = false
                        }
                    }
                    withContext(Dispatchers.IO) { kotlinx.coroutines.delay(1000) }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                showVisualAlert = false
            }
        } else {
            decibel = 0f
            showVisualAlert = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isMeasuring && hasMicPermission) {
                if (showVisualAlert && alertType == "Görsel Uyarı") {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color.Red,
                            shape = MaterialTheme.shapes.medium,
                            shadowElevation = 8.dp
                        ) {
                            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                Text("UYARI!", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(text = "${decibel.toInt()} dB", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = {
                    isMeasuring = false
                    Toast.makeText(context, "Ölçüm durduruldu", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Durdur")
                }
            } else {
                Text(text = "Gürültü Ölçer Ekranı")
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = {
                    if (hasMicPermission) {
                        isMeasuring = true
                        Toast.makeText(context, "Ölçüm başlatıldı", Toast.LENGTH_SHORT).show()
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
fun HistoryScreen(history: List<Int>, onClear: () -> Unit) {
    val context = LocalContext.current
    // Haftalık dummy veri (her gün için ortalama dB)
    val weeklyData = listOf(62, 68, 70, 65, 72, 75, 69)
    val days = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Son 1 Haftalık Gürültü Seviyesi", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(8.dp))
            // Haftalık grafik
            Canvas(modifier = Modifier
                .size(width = 300.dp, height = 120.dp)
                .padding(8.dp)) {
                if (weeklyData.isNotEmpty()) {
                    val maxVal = (weeklyData.maxOrNull() ?: 100).toFloat()
                    val minVal = (weeklyData.minOrNull() ?: 0).toFloat()
                    val stepX = size.width / (weeklyData.size - 1).coerceAtLeast(1)
                    val scaleY = if (maxVal - minVal == 0f) 1f else size.height / (maxVal - minVal)
                    val path = Path()
                    weeklyData.forEachIndexed { i, value ->
                        val x = i * stepX
                        val y = size.height - (value - minVal) * scaleY
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = Color(0xFF388E3C), style = Stroke(width = 4f))
                    // Gün isimlerini alta yaz
                    days.forEachIndexed { i, day ->
                        drawContext.canvas.nativeCanvas.apply {
                            drawText(day, i * stepX, size.height + 24f, android.graphics.Paint().apply {
                                color = android.graphics.Color.DKGRAY
                                textSize = 28f
                                textAlign = android.graphics.Paint.Align.CENTER
                            })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Text(text = "Ses Seviyesi Geçmişi", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(16.dp))
            // Eski grafik (anlık geçmiş)
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
            Button(onClick = {
                onClear()
                Toast.makeText(context, "Geçmiş temizlendi!", Toast.LENGTH_SHORT).show()
            }) {
                Text("Geçmişi Temizle")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    alertType: String,
    onAlertTypeChange: (String) -> Unit,
    alertTypes: List<String>
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Gürültü Eşiği: ${threshold.toInt()} dB", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = 50f..100f,
                steps = 5,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.size(24.dp))
            Text(text = "Uyarı Tipi", style = MaterialTheme.typography.titleMedium)
            alertTypes.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = alertType == type,
                        onClick = { onAlertTypeChange(type) }
                    )
                    Text(text = type)
                }
            }
            Spacer(modifier = Modifier.size(24.dp))
            Button(onClick = {
                Toast.makeText(context, "Ayarlar kaydedildi!", Toast.LENGTH_SHORT).show()
            }) {
                Text("Kaydet")
            }
        }
    }
}

