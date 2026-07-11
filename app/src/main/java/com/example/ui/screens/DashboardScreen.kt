package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WalkingSession
import com.example.service.WalkingForegroundService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.WalkingViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val TextMutedGrey: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)) {
        Color(0xFF5F6368)
    } else {
        Color(0xFFA3A3A3)
    }

private val TextOnObsidian: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)) {
        Color(0xFF111111)
    } else {
        Color(0xFFFFFFFF)
    }

private fun hasRequiredPermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val permission = "android.permission.ACTIVITY_RECOGNITION"
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WalkingViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isJp by viewModel.isJpLanguage.collectAsStateWithLifecycle()

    // Observe lifecycle ON_RESUME to dynamically recheck Health Connect status/permissions
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkHealthConnectPermissions()
                viewModel.refreshSyncMetadata()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // UI tabs state
    var selectedTab by remember { mutableStateOf(0) }
    
    // Core states collected from flow
    val currentServiceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val historyLog by viewModel.historyState.collectAsStateWithLifecycle()
    
    // Interactive sandbox internet state to let user simulate offline synclogs
    var isInternetOnline by remember { mutableStateOf(true) }
    
    // Tracking overlay HUD trigger
    val isTrackingActive = currentServiceState is WalkingForegroundService.ServiceState.Active

    // Permissions requesting launcher hooks
    var permissionsGrantedAlert by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Stably declared top-level Health Connect request launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.d("HealthConnect", "Dashboard Health Connect permissions request finished: granted: $granted")
        viewModel.checkHealthConnectPermissions()
        viewModel.refreshSyncMetadata()
    }

    val permissionsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add("android.permission.ACTIVITY_RECOGNITION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add("android.permission.POST_NOTIFICATIONS")
        }
        list.toTypedArray()
    }

    Scaffold(
        topBar = {
            val completedData by WalkingForegroundService.completedSession.collectAsStateWithLifecycle()
            if (!isTrackingActive && completedData == null) {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = if (isJp) "インターバル歩行セッション" else "INTERVAL SESSION",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            val isLightMode = MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)
                            Text(
                                text = if (isJp) "JIWトラッカー" else "JIW Tracker",
                                fontWeight = FontWeight.Black,
                                fontStyle = if (isLightMode) FontStyle.Italic else FontStyle.Normal,
                                color = if (isLightMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground,
                                fontSize = 21.sp,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            // Multi-language quick switch styling matching layout buttons
                            IconButton(
                                onClick = { viewModel.toggleLanguage() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = if (isJp) "EN" else "JA",
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                            
                            // Eco battery Light/Dark theme toggle styling
                            IconButton(
                                onClick = onThemeToggle,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = if (isDarkTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            val completedData by WalkingForegroundService.completedSession.collectAsStateWithLifecycle()
            if (!isTrackingActive && completedData == null) {
                val isLightMode = MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)
                val navBgColor = if (isLightMode) Color(0xFFEDF3E7) else MaterialTheme.colorScheme.surface
                val activePillColor = if (isLightMode) Color(0xFF5FAF35) else MaterialTheme.colorScheme.primary
                val activeContentColor = if (isLightMode) Color(0xFF111111) else Color.Black
                val inactiveColor = if (isLightMode) Color(0xFF7A7A7A) else Color(0xFFA3A3A3)

                NavigationBar(
                    containerColor = navBgColor,
                    tonalElevation = 12.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val items = listOf(
                        Triple(
                            if (isJp) "トレーニング" else "TRAIN", 
                            Icons.AutoMirrored.Filled.DirectionsWalk, 
                            0
                        ),
                        Triple(
                            if (isJp) "データ履歴" else "DASHBOARD", 
                            Icons.Default.Analytics, 
                            1
                        ),
                        Triple(
                            if (isJp) "テクニック" else "GUIDE", 
                            Icons.AutoMirrored.Filled.MenuBook, 
                            2
                        ),
                        Triple(
                            if (isJp) "ギア設定" else "SETTINGS", 
                            Icons.Default.Settings, 
                            3
                        )
                    )
                    // Wait, let's keep the exact same item tuples as they were to maintain functionality!

                    
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { 
                                Text(
                                    text = label, 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeContentColor,
                                selectedTextColor = if (isLightMode) Color(0xFF111111) else MaterialTheme.colorScheme.primary,
                                indicatorColor = activePillColor,
                                unselectedIconColor = inactiveColor,
                                unselectedTextColor = inactiveColor
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen rendering navigation
            val state = currentServiceState
            val completedSessionData by WalkingForegroundService.completedSession.collectAsStateWithLifecycle()

            if (completedSessionData != null) {
                SessionCompletionScreen(
                    data = completedSessionData!!,
                    isJp = isJp,
                    onGoToDashboard = {
                        selectedTab = 1
                        WalkingForegroundService.clearCompletedSession()
                    }
                )
            } else if (state is WalkingForegroundService.ServiceState.Active) {
                TrackingHudView(
                    state = state,
                    viewModel = viewModel,
                    isJp = isJp
                )
            } else {
                val onConnectHealthConnect = {
                    android.util.Log.d("HealthConnect", "Launcher requested from inner screen tab")
                    viewModel.healthConnectPermissionManager.launchPermissionRequestSafely(hcPermissionLauncher)
                }
                when (selectedTab) {
                    0 -> TrainTabScreen(viewModel, isJp, onPermissionRequest = { permissionsGrantedAlert = true })
                    1 -> DashboardStatsTabScreen(viewModel, isJp, historyLog, isInternetOnline, onInternetToggle = { isInternetOnline = !isInternetOnline }, onConnectHc = onConnectHealthConnect)
                    2 -> TechniqueTabScreen(isJp)
                    3 -> SettingsTabScreen(
                        viewModel = viewModel,
                        isJp = isJp,
                        onConnectHc = onConnectHealthConnect,
                        onNavigateToDashboard = { selectedTab = 1 }
                    )
                }
            }

            // Mock permission grant popup response
            if (permissionsGrantedAlert) {
                AlertDialog(
                    onDismissRequest = { permissionsGrantedAlert = false },
                    title = { Text(if (isJp) "身体活動データのアクセス承諾" else "Physical Activity Authorization") },
                    text = { 
                        Text(
                            if (isJp) 
                                "日本のインターバル歩行アプリは、精密なリアルタイム歩数カウントと運動効率の測定、およびケイデンスを精査するために、デバイスの歩数センサーと身体活動へのアクセスが必要です。許可しますか？"
                            else 
                                "Japanese Interval Walking requests permission to access your device's built-in step counter sensors and health statistics to compute pace, dynamic cadence, and customized active caloric expendures in real-time."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { 
                                permissionsGrantedAlert = false 
                                if (permissionsToRequest.isNotEmpty()) {
                                    permissionLauncher.launch(permissionsToRequest)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (isJp) "承諾する" else "ALLOW ACCESS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { permissionsGrantedAlert = false }) {
                            Text(if (isJp) "後で" else "CANCEL", color = TextMutedGrey)
                        }
                    }
                )
            }
        }
    }
}

// ==========================================
// TAB 0: TRAINING PRESETS & ACTIONS
// ==========================================
@Composable
fun TrainTabScreen(
    viewModel: WalkingViewModel,
    isJp: Boolean,
    onPermissionRequest: () -> Unit
) {
    val context = LocalContext.current
    val selectedPresetIndex by viewModel.selectedPresetIndex.collectAsStateWithLifecycle()
    
    // Read custom presets limits dynamically
    val customSlow by viewModel.customSlowMinutes.collectAsStateWithLifecycle()
    val customFast by viewModel.customFastMinutes.collectAsStateWithLifecycle()
    val customCycles by viewModel.customCycles.collectAsStateWithLifecycle()

    val presets = listOf(
        PresetItem(
            nameEn = "Beginner Warmup",
            nameJp = "初心者お試し",
            emoji = Icons.Default.SelfImprovement,
            slowMin = 3,
            fastMin = 3,
            cycles = 2,
            descEn = "Perfect gentle introduction. 2 cycles (12 min total) of interval pacing.",
            descJp = "初めての方におすすめ。3分低速＆3分高速を2交代（計12分）。"
        ),
        PresetItem(
            nameEn = "Standard Workout",
            nameJp = "標準トレーニング",
            emoji = Icons.AutoMirrored.Filled.DirectionsWalk,
            slowMin = 3,
            fastMin = 3,
            cycles = 5,
            descEn = "The university blueprint standard. 5 alternating cycles (30 min total).",
            descJp = "信州大学の研究に基づく王道処方。ゆっくり＆早歩きを5交代（計30分）。"
        ),
        PresetItem(
            nameEn = "Advanced Stamina",
            nameJp = "持久力強化",
            emoji = Icons.Default.ElectricBolt,
            slowMin = 3,
            fastMin = 3,
            cycles = 8,
            descEn = "Peak physical endurance builder. 8 continuous cycles (48 min total).",
            descJp = "心肺能力をさらに高める高強度コース。8交代（計48分）。"
        ),
        PresetItem(
            nameEn = "My Custom Interval",
            nameJp = "自作カスタム設定",
            emoji = Icons.Default.Tune,
            slowMin = customSlow,
            fastMin = customFast,
            cycles = customCycles,
            descEn = "Based on settings configurations ($customSlow min slow / $customFast min fast, $customCycles cycles).",
            descJp = "設定タブで保存した自由な間隔（ゆっくり${customSlow}分/早歩き${customFast}分、計${customCycles}交代）。"
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)
    ) {
        item {
            // Nike Run style bold header
            Text(
                text = if (isJp) "目標を設定せよ" else "CHOOSE YOUR WORKOUT",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = if (isJp) 
                    "3分間のゆっくり歩きと、3分間の全力の早歩きを繰り返すことで、効率よく筋力と心肺能力をアップ。" 
                else 
                    "Alternate 3-min slow recovery walks with 3-min maximum fast walks to increase VO₂ max by up to 10%.",
                fontSize = 13.sp,
                color = TextMutedGrey,
                lineHeight = 18.sp
            )
        }

        item {
            // Bold sport start button moved to TOP
            Button(
                onClick = {
                    if (hasRequiredPermissions(context)) {
                        val p = presets.getOrElse(selectedPresetIndex) { presets[1] }
                        viewModel.startWorkout(
                            slowMin = p.slowMin,
                            fastMin = p.fastMin,
                            cycles = p.cycles,
                            preset = if (isJp) p.nameJp else p.nameEn
                        )
                    } else {
                        onPermissionRequest() // Gracefully push sensor authorization request
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    text = if (isJp) "速歩トレーニングを開始する" else "START INTERVAL WALK",
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp,
                    fontSize = 16.sp
                )
            }
        }

        items(presets.size) { index ->
            val preset = presets[index]
            val isSelected = selectedPresetIndex == index
            
            val totalMinutes = (preset.slowMin + preset.fastMin) * preset.cycles
            val cycleName = if (isJp) "${preset.cycles} 交代" else "${preset.cycles} cycles"

            Card(
                onClick = { viewModel.setSelectedPresetIndex(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = preset.emoji,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (isJp) preset.nameJp else preset.nameEn,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isJp) preset.descJp else preset.descEn,
                                    fontSize = 11.sp,
                                    color = TextMutedGrey,
                                    modifier = Modifier.padding(top = 2.dp),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatsBadge(
                            label = if (isJp) "目標時間" else "TOTAL TIME",
                            value = "$totalMinutes min",
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatsBadge(
                            label = if (isJp) "インターバル" else "STRUCTURE",
                            value = cycleName,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }


    }
}

data class PresetItem(
    val nameEn: String,
    val nameJp: String,
    val emoji: ImageVector,
    val slowMin: Int,
    val fastMin: Int,
    val cycles: Int,
    val descEn: String,
    val descJp: String
)

@Composable
fun StatsBadge(label: String, value: String, color: Color) {
    Column {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey, letterSpacing = 0.5.sp)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
    }
}

// ==========================================
// TAB 1: DASHBOARD, PERSONAL RECORD & SYNCS
// ==========================================
@Composable
fun DashboardStatsTabScreen(
    viewModel: WalkingViewModel,
    isJp: Boolean,
    history: List<WalkingSession>,
    isInternetOnline: Boolean,
    onInternetToggle: () -> Unit,
    onConnectHc: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val syncStatus by viewModel.syncingProgress.collectAsStateWithLifecycle()
    val isGfConnected by viewModel.isGoogleFitConnected.collectAsStateWithLifecycle()
    val isHcConnected by viewModel.isHealthConnectConnected.collectAsStateWithLifecycle()
    val hasHCPermissions by viewModel.hasHealthConnectPermissions.collectAsStateWithLifecycle()
    val lastSyncMetadata by viewModel.lastSyncMetadata.collectAsStateWithLifecycle()

    // Share dialog record holder
    var activeShareSession by remember { mutableStateOf<WalkingSession?>(null) }

    // Aggregate statistics
    val totalSessions = history.size
    val totalBurn = history.sumOf { it.calories }
    val totalSteps = history.sumOf { it.steps }
    val totalMinutes = history.sumOf { it.durationSeconds } / 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)
    ) {
        item {
            // Dashboard bold title
            Text(
                text = if (isJp) "パーソナルスタッツ" else "Your Progress",
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = if (isJp) "蓄積された運動科学データと同期管理" else "Your scientific training volume and cloud logs",
                fontSize = 12.sp,
                color = TextMutedGrey
            )
        }

        // Giant stats strip (Nike Run style)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        valueFormatted(totalSessions.toDouble(), 0), 
                        fontWeight = FontWeight.Black, 
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary, 
                        fontSize = 32.sp
                    )
                    Text(if (isJp) "セッション" else "SESSIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        valueFormatted(totalMinutes.toDouble(), 0), 
                        fontWeight = FontWeight.Black, 
                        fontStyle = FontStyle.Italic,
                        color = TextOnObsidian, 
                        fontSize = 32.sp
                    )
                    Text(if (isJp) "合計分" else "TOTAL MINUTES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        valueFormatted(totalBurn, 0), 
                        fontWeight = FontWeight.Black, 
                        fontStyle = FontStyle.Italic,
                        color = LaserCrimson, 
                        fontSize = 32.sp
                    )
                    Text("TOTAL KCAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                }
            }
        }

        // Interactive health integration card (Redesigned exclusively for Health Connect)
        item {
            val sdkStatus = remember { viewModel.healthConnectManager.getSdkStatus() }

            val isLightMode = MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync, 
                                contentDescription = "Sync status", 
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isJp) "Android ヘルスコネクト連携" else "Android Health Connect Sync",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))

                        // Redesigned compact status chip using theme variables and soft containers
                        val (statusText, statusBg, statusColor) = when (sdkStatus) {
                            androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> {
                                if (hasHCPermissions) {
                                    Triple(
                                        if (isJp) "接続中" else "Connected",
                                        SystemSuccess.copy(alpha = 0.12f),
                                        SystemSuccess
                                    )
                                } else {
                                    Triple(
                                        if (isJp) "アクセス許可が必要" else "Permissions Required",
                                        SystemWarning.copy(alpha = 0.12f),
                                        SystemWarning
                                    )
                                }
                            }
                            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                Triple(
                                    if (isJp) "更新が必要" else "Update Required",
                                    SystemWarning.copy(alpha = 0.12f),
                                    SystemWarning
                                )
                            }
                            else -> {
                                Triple(
                                    if (isJp) "非対応" else "Not Supported",
                                    SystemError.copy(alpha = 0.12f),
                                    SystemError
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusBg)
                                .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isJp) 
                            "毎日の運動で消費したカロリー、歩数、距離、およびインターバル速歩（ゆっくり・早歩き）履歴をAndroid標準ヘルスコネクトに直接同期し、Google Fitなどの他健康管理アプリと自動連携します。"
                        else 
                            "Safely syncs calories, interval walking cycles, distance, and stepped volumes with Android's persistent local Health Connect repository (composing automatically with Google Fit).",
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val isAvailable = sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

                    if (isAvailable) {
                        // Display Sync Statistics if connected
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isJp) "最終同期実績" else "LAST SYNCDATA STATUS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val formattedSyncTime = remember(lastSyncMetadata) {
                                    if (lastSyncMetadata.lastSyncTimestamp > 0L) {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                        sdf.format(java.util.Date(lastSyncMetadata.lastSyncTimestamp))
                                    } else {
                                        if (isJp) "未同期" else "No sync yet"
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = if (isJp) "最終同期日時:" else "Sync Time:", fontSize = 11.sp, color = TextMutedGrey)
                                    Text(text = formattedSyncTime, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = if (isJp) "同期歩数:" else "Synced Steps:", fontSize = 11.sp, color = TextMutedGrey)
                                    Text(text = if (lastSyncMetadata.lastSyncTimestamp > 0L) "${lastSyncMetadata.lastSyncedStepCount} steps" else "-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = if (isJp) "同期カロリー:" else "Synced Calories:", fontSize = 11.sp, color = TextMutedGrey)
                                    Text(text = if (lastSyncMetadata.lastSyncTimestamp > 0L) String.format(java.util.Locale.getDefault(), "%.1f kcal", lastSyncMetadata.lastSyncedCalories) else "-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (hasHCPermissions) {
                            Button(
                                onClick = { viewModel.syncUnsyncedSessions() },
                                enabled = syncStatus !is WalkingViewModel.SyncStatus.Syncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync, 
                                        contentDescription = "Sync now action",
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isJp) "ヘルスコネクトへ即時同期する" else "TRIGGER HEALTH SYNC NOW",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    android.util.Log.d("HealthConnect", "Dashboard card MANAGE PERMISSIONS button clicked")
                                    onConnectHc()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings, 
                                        contentDescription = "Request setup permissions",
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isJp) "アクセス権限を許可する" else "MANAGE PERMISSIONS",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    } else if (sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(viewModel.healthConnectManager.getPlayStoreIntent())
                                } catch (e: Exception) {
                                    android.util.Log.e("DashboardScreen", "Error opening Play Store helper", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DownloadForOffline, 
                                    contentDescription = "Install or update action",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isJp) "ヘルスコネクトを更新する" else "UPDATE HEALTH CONNECT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Not supported on device, warning message only
                        Text(
                            text = if (isJp) "お使いのシステムはヘルスコネクトに対応していません。" else "Health Connect is not supported on this legacy device layout.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Display sync progress state beautifully
                    when (val status = syncStatus) {
                        is WalkingViewModel.SyncStatus.Syncing -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column {
                                LinearProgressIndicator(
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isJp) "暗号化同期セッションをヘルスコネクトに保存中..." else "Syncing interval steps to local Health Connect...",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                        is WalkingViewModel.SyncStatus.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success tick icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isJp) "同期完了！新しい履歴を同期しました。" else "Sync completed! Offline interval logs written to Health Connect.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is WalkingViewModel.SyncStatus.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = "Failure warning icon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> { /* Idle */ }
                    }
                }
            }
        }

        // Workout Logs Section Header
        item {
            Text(
                text = if (isJp) "トレーニング履歴" else "COMPLETED RUNS & WALKS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp,
                color = TextMutedGrey,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk, 
                            contentDescription = "empty runs", 
                            tint = TextMutedGrey, 
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isJp) "歩行セッションの記録はありません" else "No walking sessions registered.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isJp) "上の「トレーニング」タブから開始しよう！" else "Initiate your first scientific workout interval on the workout catalog.",
                            fontSize = 11.sp,
                            color = TextMutedGrey,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(history) { session ->
                HistoryRowCard(
                    session = session,
                    isJp = isJp,
                    onShareClick = { activeShareSession = session },
                    onDeleteClick = { viewModel.deleteLoggedSession(session) }
                )
            }
        }
    }

    // Interactive Social Sharing Overlay Dialog
    activeShareSession?.let { session ->
        val shareData = viewModel.getShareContent(session)
        AlertDialog(
            onDismissRequest = { activeShareSession = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "share", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isJp) "セッションカードを共有" else "Share Training Success")
                }
            },
            text = {
                Column {
                    Text(
                        text = if (isJp) "以下の一文をクリップボードにコピーしてStrava、Nike Run、またはSNSで共有に利用できます：" else "Copy your training stats card format directly for Strava, Adidas Run, or Instagram updates:",
                        fontSize = 12.sp,
                        color = TextMutedGrey,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarbonBlack)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = shareData.second,
                            fontSize = 11.sp,
                            color = TextOnObsidian,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(shareData.second))
                        activeShareSession = null
                        // Display clean overlay confirmation toast
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isJp) "コピーする" else "COPY STACK", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeShareSession = null }) {
                    Text(if (isJp) "閉じる" else "CLOSE", color = TextMutedGrey)
                }
            }
        )
    }
}

@Composable
fun SyncTargetBadge(name: String, isConnected: Boolean, isJp: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isConnected) Color(0xFF16252C) else Color(0xFF202023))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isConnected) MaterialTheme.colorScheme.primary else TextMutedGrey)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$name: ${if (isConnected) (if(isJp)"接続中" else "CONNECTED") else (if(isJp) "切断" else "OFF")}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConnected) Color.White else TextMutedGrey
        )
    }
}

@Composable
fun HistoryRowCard(
    session: WalkingSession,
    isJp: Boolean,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFmt = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(session.dateMillis))
    
    val minuteFmt = session.durationSeconds / 60
    val secFmt = session.durationSeconds % 60
    val durationStr = String.format("%02d:%02d", minuteFmt, secFmt)

    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = dateFmt, fontSize = 11.sp, color = TextMutedGrey)
                    Text(
                        text = "${session.steps} ${if (isJp) "歩" else "steps"}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sync clouds checklist symbols
                    if (session.isSyncedToGoogleFit) {
                        Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "gf checked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                    if (session.isSyncedToHealthConnect) {
                        Icon(imageVector = Icons.Default.Favorite, contentDescription = "hc checked", tint = LaserCrimson, modifier = Modifier.size(14.dp))
                    }
                    if (!session.isSyncedToGoogleFit && !session.isSyncedToHealthConnect) {
                        Icon(imageVector = Icons.Default.CloudOff, contentDescription = "unsynced flag", tint = TextMutedGrey, modifier = Modifier.size(14.dp))
                    }

                    Box {
                        IconButton(onClick = { expandedMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "options logo", tint = TextMutedGrey)
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isJp) "コピーして共有" else "Copy & Share Stats") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = "share") },
                                onClick = {
                                    expandedMenu = false
                                    onShareClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isJp) "記録を消去" else "Delete Session Log") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "delete", tint = LaserCrimson) },
                                onClick = {
                                    expandedMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Substantial metric grid in bold columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "TIME", valStr = durationStr)
                MetricColumn(label = "CALORIES", valStr = "${String.format("%.1f", session.calories)} kcal")
                MetricColumn(label = "AVG CADENCE", valStr = "${String.format("%.0f", session.avgCadence)} spm")
                MetricColumn(label = "AVG PACE", valStr = "${String.format("%.1f", session.avgPace)} m/k")
            }
            
            Text(
                text = if (isJp) 
                    "インターバル交代数: ${session.totalCycles} サイクル (低速: ${session.slowCyclesCount}分 / 高速: ${session.fastCyclesCount}分)"
                else 
                    "Interval breakdown: ${session.totalCycles} cycles (Recovery: ${session.slowCyclesCount}m / Speed: ${session.fastCyclesCount}m)",
                fontSize = 10.sp,
                color = TextMutedGrey,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun MetricColumn(label: String, valStr: String) {
    Column {
        Text(text = label, fontSize = 8.sp, color = TextMutedGrey, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(text = valStr, fontSize = 12.sp, color = TextOnObsidian, fontWeight = FontWeight.Black)
    }
}

fun valueFormatted(num: Double, digits: Int): String {
    return String.format(Locale.getDefault(), "%.${digits}f", num)
}

// ==========================================
// TAB 2: METICULOUS PRACTICE GUIDE (SCIENCE)
// ==========================================
@Composable
fun TechniqueTabScreen(isJp: Boolean) {
    var expandedIndex by remember { mutableStateOf(-1) }

    val chapters = listOf(
        GuideChapter(
            titleEn = "1. Simple & Easy Posture",
            titleJp = "1. かんたん正しい姿勢",
            icon = Icons.Default.AccessibilityNew,
            tipsEn = listOf(
                "Look straight ahead comfortably (about 10 to 20 steps in front of you). Try not to look down at your feet.",
                "Relax your shoulders and keep your chest open. Let your arms swing naturally.",
                "Stand tall but loose. Keep your back straight without tensing up.",
                "Ensure your body feels light and balanced from head to toe, letting you walk further without feeling tired."
            ),
            tipsJp = listOf(
                "10歩から20歩ほど先の少し遠くを見るように、すっきりとまっすぐ前を向いて歩きましょう。下は見すぎないようにします。",
                "肩の力をふっと抜いて、胸を軽く開きます。こうすると呼吸がぐっと楽になります。",
                "背すじを優しくすっと伸ばします。お腹をほんの少し意識すると、腰が痛くなりにくく安定します。",
                "頭のてっぺんから足の裏まで、一本の軸が通っているようなイメージで、軽やかに立ちます。"
            )
        ),
        GuideChapter(
            titleEn = "2. Smart Steps & Landing",
            titleJp = "2. 歩幅と足先のコツ",
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            tipsEn = listOf(
                "During EASY walk intervals: Just walk at your usual, comfortable and relaxed pace.",
                "During FAST walk intervals: Take slightly larger steps, about one finger-width wider than your normal step.",
                "Land softly on your heels first, and let your body weight roll smoothly from heel to toe.",
                "Push off the ground gently using your toes. This naturally moves you forward with less effort!",
                "Listen to the app's sound or vibe indicators to know exactly when to shift between easy and fast walking."
            ),
            tipsJp = listOf(
                "「ゆっくり歩く」時間：いつものお買い物と同じように、のんびりとリラックスして歩いて息を整えましょう。",
                "「早歩き」時間：いつもの歩き方より、拳ひとつ分（ほんの3〜5センチ）大股で踏み出すように意識します。",
                "足元は「かかと」から優しく着地し、足の裏全体を転がすように、つま先へと滑らかに体重を移動させます。",
                "地面を蹴るときは、足の指先で優しく押し出すイメージ。これだけで驚くほど足が前に進みます。",
                "アプリがタイミングを教えてくれるので、音やスマートフォンの振動に合わせて気軽に切り替えるだけでOKです。"
            )
        ),
        GuideChapter(
            titleEn = "3. Relaxed Arm Swinging",
            titleJp = "3. 腕の軽いスイング",
            icon = Icons.Default.FitnessCenter,
            tipsEn = listOf(
                "Bend your elbows slightly (around 90 degrees) to let them move efficiently.",
                "Swing your arms backward gently from the shoulder. Think of pulling your shoulder blades back.",
                "Keep your hands moving in a straight, comfortable forward-backward direction rather than crossing over your chest.",
                "Don't squeeze your fists tightly. Keep your hands relaxed, as if gently holding a warm egg.",
                "A natural, healthy arm swing automatically makes your legs move faster and lighter!"
            ),
            tipsJp = listOf(
                "肘は『約90度』に軽く曲げると、腕が振りやすく歩きやすくなります。",
                "手先だけで振るのではなく、肩甲骨を後ろに引くように『肩の周りを大きく動かす運動』として腕を振ります。",
                "腕は胸の前で交差させず、まっすぐ前後にリズムよくスイングすることを意識します。",
                "拳をぎゅっと硬く握りしめず、小さなマシュマロか温かい卵をそっと持っているように手をゆるめます。",
                "腕をしっかりスイングさせると、そのリズムに合わせて自然に歩幅が広がり、スピードが上がります。"
            )
        ),
        GuideChapter(
            titleEn = "4. Natural Breathing Rhythm",
            titleJp = "4. はずむ呼吸リズム",
            icon = Icons.Default.Air,
            tipsEn = listOf(
                "Breathe deeply through your nose and blow out softly through your mouth. This keeps your heart feeling calm and steady.",
                "Try matching your breaths to your footsteps! For example: take 3 steps while inhaling, and then 3 steps while exhaling.",
                "Let your belly expand comfortably when inhaling, and soften when you blow out.",
                "Don't hold your breath! Keep breathing evenly and comfortably even when walking fast."
            ),
            tipsJp = listOf(
                "鼻からゆっくり空気を吸い込み、お口からふーっと優しく吹き出す呼吸を行うと、心拍数が安定して楽に歩けます。",
                "お好みのリズムで呼吸を歩数と合わせてみましょう。例：「3歩歩きながら吸う、次の3歩で吐き出す」",
                "胸だけで息をせず、お腹を膨らませたり凹ませたりする深呼吸のようなお腹での呼吸がおすすめです。",
                "早歩きのときも決して息を止めず、常に新鮮な空気を取り入れながらニコニコ歩けるペースを心がけます。"
            )
        ),
        GuideChapter(
            titleEn = "5. Why 30 Mins Works So Well",
            titleJp = "5. どうして30分で効果が出るの？",
            icon = Icons.Default.Science,
            tipsEn = listOf(
                "Designed based on medical research showing incredible health results in everyday life.",
                "Instead of exhausting yourself with 10,000 steps a day, this 30-minute interval routine gives you the same premium fitness results.",
                "Alternating easy and fast walks wakes up your leg muscles, boosts your metabolism, and leaves you feeling energetic!",
                "Perfect for busy adults who want the absolute best benefits with zero gym fees and zero wasted time."
            ),
            tipsJp = listOf(
                "信州大学医学部で長年研究され、多くの人々で効果が実証された健康に安全なメソッドに基づいています。",
                "時間のない毎日でも、ジムに通わずに週に数回、1日わずか30分歩くだけで理想的な体力向上と健康維持を実感できます。",
                "強弱をつける歩き方が眠っていた筋肉と代謝スイッチをパッとONにし、短時間で効果的に体脂肪を燃焼させます。",
                "ただダラダラと1万歩を目標に歩くよりも、はるかに短時間で、身体が若々しく元気に生まれ変わります。"
            )
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)
    ) {
        item {
            Text(
                text = if (isJp) "メリハリ歩き・基本ガイド" else "WALKING TIPS",
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = if (isJp) "毎日をもっと元気に、たった30分で最高に楽しむコツ" else "Easy steps to stay extremely healthy and fit",
                fontSize = 12.sp,
                color = TextMutedGrey
            )
        }

        items(chapters.size) { index ->
            val ch = chapters[index]
            val isExpanded = expandedIndex == index

            val isLightMode = MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)
            val cardElevation = if (isExpanded) 10.dp else 4.dp
            val borderColor = if (isExpanded) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isLightMode) Color(0xFFDDE5D8) else MaterialTheme.colorScheme.outline
            }

            Card(
                onClick = { expandedIndex = if (isExpanded) -1 else index },
                colors = CardDefaults.cardColors(
                    containerColor = if (isLightMode) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ch.icon,
                                contentDescription = null,
                                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(24.dp)
                            )
                            Text(
                                text = if (isJp) ch.titleJp else ch.titleEn,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "expand icon",
                            tint = if (isLightMode) Color(0xFF7A7A7A) else TextMutedGrey
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val activeTips = if (isJp) ch.tipsJp else ch.tipsEn
                        activeTips.forEachIndexed { i, tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "0${i + 1}.",
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                                )
                                Text(
                                    text = tip,
                                    fontSize = 12.sp,
                                    color = if (isLightMode) Color(0xFF111111) else TextOnObsidian,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class GuideChapter(
    val titleEn: String,
    val titleJp: String,
    val icon: ImageVector,
    val tipsEn: List<String>,
    val tipsJp: List<String>
)

// ==========================================
// TAB 3: SYSTEM SETTINGS, DURATION PARAMETERS
// ==========================================
@Composable
fun SettingsTabScreen(
    viewModel: WalkingViewModel,
    isJp: Boolean,
    onConnectHc: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    
    // Core parameters from preferences
    val weight by viewModel.userWeight.collectAsStateWithLifecycle()
    val isWeightUnitKg by viewModel.isWeightUnitKg.collectAsStateWithLifecycle()
    val weightStr = remember(weight, isWeightUnitKg) {
        val wt = if (isWeightUnitKg) weight else weight * 2.20462f
        mutableStateOf(String.format(Locale.US, "%.1f", wt))
    }
    
    val voiceEnabled by viewModel.isVoiceEnabled.collectAsStateWithLifecycle()
    val audioEnabled by viewModel.isAudioEnabled.collectAsStateWithLifecycle()
    
    val customSlow by viewModel.customSlowMinutes.collectAsStateWithLifecycle()
    val customFast by viewModel.customFastMinutes.collectAsStateWithLifecycle()
    val customCycles by viewModel.customCycles.collectAsStateWithLifecycle()

    val gfSyncOn by viewModel.isGoogleFitConnected.collectAsStateWithLifecycle()
    val hcSyncOn by viewModel.isHealthConnectConnected.collectAsStateWithLifecycle()

    val remindOn by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val remindHr by viewModel.reminderHour.collectAsStateWithLifecycle()
    val remindMin by viewModel.reminderMinute.collectAsStateWithLifecycle()
    val hasHCPermissions by viewModel.hasHealthConnectPermissions.collectAsStateWithLifecycle()
    val lastSyncMetadata by viewModel.lastSyncMetadata.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncingProgress.collectAsStateWithLifecycle()

    var showWeightDialog by remember { mutableStateOf(false) }
    var showCustomIntervalDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)
    ) {
        item {
            Text(
                text = if (isJp) "設定" else "Settings",
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = if (isJp) "設定と毎日のリマインダー" else "Your settings and daily reminders",
                fontSize = 12.sp,
                color = TextMutedGrey
            )
        }

        // Custom Interval Durations
        item {
            IntervalSettingsCard(
                customSlow = customSlow,
                customFast = customFast,
                customCycles = customCycles,
                isJp = isJp,
                onClick = { showCustomIntervalDialog = true }
            )
        }

        // Personalized Weights for exact MET burns
        item {
            PerformanceWeightCard(
                weight = weight,
                isWeightUnitKg = isWeightUnitKg,
                isJp = isJp,
                onUnitToggle = { viewModel.toggleWeightUnit() },
                onClick = { showWeightDialog = true }
            )
        }

        // Onboarding walkthrough / Tour / Reset state
        item {
            OnboardingSettingsCard(
                isJp = isJp,
                onReplay = { viewModel.replayAppTour() },
                onReset = { viewModel.resetOnboarding() }
            )
        }

        // Connectors for Android Health Connect (Redesigned)
        item {
            val sdkStatus = remember { viewModel.healthConnectManager.getSdkStatus() }

            val isLightMode = MaterialTheme.colorScheme.background != Color(0xFF0F0F0F)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isJp) "ヘルスコネクト連携管理" else "HEALTH CONNECT INTEGRATION",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Redesigned status chip with separate light-theme compliance
                        val (statusText, statusBg, statusColor) = when (sdkStatus) {
                            androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> {
                                if (hasHCPermissions) {
                                    Triple(
                                        if (isJp) "接続中" else "Connected",
                                        SystemSuccess.copy(alpha = 0.12f),
                                        SystemSuccess
                                    )
                                } else {
                                    Triple(
                                        if (isJp) "アクセス許可が必要" else "Permissions Required",
                                        SystemWarning.copy(alpha = 0.12f),
                                        SystemWarning
                                    )
                                }
                            }
                            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                Triple(
                                    if (isJp) "更新が必要" else "Update Required",
                                    SystemWarning.copy(alpha = 0.12f),
                                    SystemWarning
                                )
                            }
                            else -> {
                                Triple(
                                    if (isJp) "非対応" else "Not Supported",
                                    SystemError.copy(alpha = 0.12f),
                                    SystemError
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusBg)
                                .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = statusText.uppercase(Locale.getDefault()),
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isJp) 
                            "ヘルスコネクトは健康管理データを安全に共有するためのAndroidプラットフォーム機能です。Google Fit、歩数計、その他の健康管理アプリと本アプリの歩数、カロリー情報を統合できます。"
                        else 
                            "Health Connect is a secure local Android service linking major health trackers. Seamlessly share intermediate, speed-profiled interval telemetry with Google Fit on your device.",
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val isAvailable = sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

                    when {
                        sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE -> {
                            Text(
                                text = if (isJp) "お使いのシステムはヘルスコネクトに対応していません。" else "Health Connect is not supported on this legacy device layout.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isJp) "ヘルスコネクトの更新が必要です。" else "Health Connect requires an update.",
                                    fontSize = 11.sp,
                                    color = SystemWarning,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(viewModel.healthConnectManager.getPlayStoreIntent())
                                        } catch (e: Exception) {
                                            android.util.Log.e("Settings", "Failed opening Play Store help link", e)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(if (isJp) "ヘルスコネクトを更新する" else "UPDATE HEALTH CONNECT", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        !hasHCPermissions -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SystemWarning.copy(alpha = 0.15f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info, 
                                        contentDescription = "Permission requirements", 
                                        tint = SystemWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isJp) "運動データを書き込むために権限の承認が必要です。" else "Japanese Interval Walking requires reading & writing permissions.",
                                        fontSize = 11.sp,
                                        color = SystemWarning,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        android.util.Log.d("HealthConnect", "Dashboard settings MANAGE PERMISSIONS button clicked")
                                        onConnectHc()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Text(if (isJp) "アクセス権限を許可する" else "MANAGE PERMISSIONS", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SystemSuccess.copy(alpha = 0.15f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified, 
                                        contentDescription = "Permissions granted", 
                                        tint = SystemSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isJp) "接続済み" else "Connected",
                                        fontSize = 12.sp,
                                        color = SystemSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = onNavigateToDashboard,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (isJp) "同期を管理" else "Manage Sync",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vocal TTS Coaches toggles Screen
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isJp) "音声案内コーチ" else "Sounds & Voice",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    ToggleConfigRow(
                        title = if (isJp) "リアルタイムTTSボイスアシスト" else "Real-time Voice Assistant (TTS)",
                        desc = if (isJp) "「早歩き！」「ゆっくり！」など交代の指示を音声化" else "Speak vocal speed instructions on interval transitions",
                        checked = voiceEnabled,
                        onCheckChange = { viewModel.toggleVoice() }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                    ToggleConfigRow(
                        title = if (isJp) "ピピッという電子音アラート" else "Sound Beep Cues",
                        desc = if (isJp) "区間の切り替わり寸前にブザーでチャイムを鳴らす" else "Trigger high acoustic bleeps on phase completions",
                        checked = audioEnabled,
                        onCheckChange = { viewModel.toggleAudio() }
                    )
                }
            }
        }

        // Reminders configurations Card
        item {
            WorkoutReminderSettingCard(
                enabled = remindOn,
                hour = remindHr,
                minute = remindMin,
                isJp = isJp,
                onRemindToggle = { viewModel.toggleReminder() },
                onAlarmClick = { showAlarmDialog = true }
            )
        }

        // Risk Data Operations Box
        item {
            DangerZoneCard(
                isJp = isJp,
                onClearAll = { viewModel.clearAllHistoryLogs() }
            )
        }
    }

    // Weight Calibrator Dialog Popup
    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text(if (isJp) "体重の設定" else "Set Your Body Weight") },
            text = {
                Column {
                    Text(
                        text = if (isJp) {
                            if (isWeightUnitKg) "よりあなたの歩行状態に合わせた、正確な消費カロリーをスマートに計算します。体重(kg)："
                            else "よりあなたの歩行状態に合わせた、正確な消費カロリーをスマートに計算します。体重(lb)："
                        } else {
                            if (isWeightUnitKg) "Entering your body weight helps us estimate your burned calories accurately during your 30-minute walking sessions. Weight (kg):"
                            else "Entering your body weight helps us estimate your burned calories accurately during your 30-minute walking sessions. Weight (lb):"
                        },
                        fontSize = 12.sp,
                        color = TextMutedGrey,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = weightStr.value,
                        onValueChange = { weightStr.value = it },
                        label = { Text(if (isJp) (if (isWeightUnitKg) "体重 (kg)" else "体重 (lb)") else (if (isWeightUnitKg) "Weight (kg)" else "Weight (lb)")) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = weightStr.value.toFloatOrNull() ?: 70f
                        val parsedInKg = if (isWeightUnitKg) parsed else parsed / 2.20462f
                        viewModel.setOnboardingWeight(parsedInKg)
                        showWeightDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isJp) "体重を保存" else "SAVE WEIGHT", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text(if (isJp) "キャンセル" else "CANCEL", color = TextMutedGrey)
                }
            }
        )
    }

    // Custom Intervals configurator dialog
    if (showCustomIntervalDialog) {
        var slowVal by remember { mutableStateOf(customSlow) }
        var fastVal by remember { mutableStateOf(customFast) }
        var cyclesVal by remember { mutableStateOf(customCycles) }

        AlertDialog(
            onDismissRequest = { showCustomIntervalDialog = false },
            title = { Text(if (isJp) "自由カスタム交代設定" else "Configure Custom Walk Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (isJp) "あなた独自のオリジナル科学的歩行ペース間隔を設定します。" else "Program asymmetric pacing equations to match personal conditioning targets.", fontSize = 11.sp, color = TextMutedGrey)
                    
                    IntSliderGroup(
                        label = if (isJp) "ゆっくり時間 (分)" else "SLOW DURATION",
                        currentVal = slowVal,
                        minRange = 1,
                        maxRange = 15,
                        onValChange = { slowVal = it }
                    )
                    IntSliderGroup(
                        label = if (isJp) "早歩き時間 (分)" else "FAST DURATION",
                        currentVal = fastVal,
                        minRange = 1,
                        maxRange = 15,
                        onValChange = { fastVal = it }
                    )
                    IntSliderGroup(
                        label = if (isJp) "インターバル交代数" else "INTERVAL CYCLES",
                        currentVal = cyclesVal,
                        minRange = 1,
                        maxRange = 20,
                        onValChange = { cyclesVal = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCustomIntervals(slowVal, fastVal, cyclesVal)
                        showCustomIntervalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isJp) "設定を保存" else "SAVE INTERVALS", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomIntervalDialog = false }) {
                    Text(if (isJp) "戻る" else "CANCEL", color = TextMutedGrey)
                }
            }
        )
    }

    // Alarm reminder configurator Android TimePickerDialog dialog
    if (showAlarmDialog) {
        val currentContext = LocalContext.current
        val hourPicker = TimePickerDialog(
            currentContext,
            { _, hour, minute ->
                viewModel.updateReminderHourAndMin(hour, minute)
                showAlarmDialog = false
            },
            remindHr,
            remindMin,
            true
        )
        LaunchedEffect(Unit) {
            hourPicker.show()
        }
    }
}

@Composable
fun IntSliderGroup(label: String, currentVal: Int, minRange: Int, maxRange: Int, onValChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMutedGrey)
            Text("$currentVal", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = currentVal.toFloat(),
            onValueChange = { onValChange(it.toInt()) },
            valueRange = minRange.toFloat()..maxRange.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun ToggleConfigRow(title: String, desc: String, checked: Boolean, onCheckChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(desc, fontSize = 10.sp, color = TextMutedGrey, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = TextMutedGrey,
                uncheckedTrackColor = ObsidianCard
            )
        )
    }
}

@Composable
fun IntervalSettingsCard(customSlow: Int, customFast: Int, customCycles: Int, isJp: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Timer, contentDescription = "interval settings", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isJp) "カスタム間歩の時間設定" else "Your Interval Settings",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isJp) "ゆっくり ${customSlow} 分 / 早歩き ${customFast} 分 · ${customCycles}サイクル" else "Recovery: $customSlow m / Sprint: $customFast m · $customCycles cycles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit interval", tint = TextMutedGrey, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun PerformanceWeightCard(
    weight: Float,
    isWeightUnitKg: Boolean,
    isJp: Boolean,
    onUnitToggle: () -> Unit,
    onClick: () -> Unit
) {
    val displayWeight = if (isWeightUnitKg) {
        String.format(Locale.US, "%.1f kg", weight)
    } else {
        String.format(Locale.US, "%.1f lb", weight * 2.20462f)
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Scale, contentDescription = "weight setting", tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isJp) "消費カロリー調整プロファイル" else "CALORIES & WEIGHT",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isJp) "あなたの体重 : $displayWeight" else "Your weight: $displayWeight",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    
                    // Segmented control
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val kgBg = if (isWeightUnitKg) MaterialTheme.colorScheme.primary else Color.Transparent
                        val kgColor = if (isWeightUnitKg) Color.Black else MaterialTheme.colorScheme.onSurface
                        val lbBg = if (!isWeightUnitKg) MaterialTheme.colorScheme.primary else Color.Transparent
                        val lbColor = if (!isWeightUnitKg) Color.Black else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(kgBg)
                                .clickable { if (!isWeightUnitKg) onUnitToggle() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("kg", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = kgColor)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(lbBg)
                                .clickable { if (isWeightUnitKg) onUnitToggle() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("lb", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lbColor)
                        }
                    }
                }
            }
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit weight", tint = TextMutedGrey, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun OnboardingSettingsCard(
    isJp: Boolean,
    onReplay: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = "onboarding settings",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isJp) "アプリオンボーディングツアー設定" else "ONBOARDING & TOUR PREFERENCES",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isJp) "初回ツアーの再起動や設定の初期化" else "Replay app walkthroughs and reset setup logs",
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReplay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (isJp) "ツアー再再生" else "REPLAY TOUR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onReset,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (isJp) "初期化する" else "RESET TOUR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutReminderSettingCard(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    isJp: Boolean,
    onRemindToggle: () -> Unit,
    onAlarmClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = if (isJp) "毎日リマインダー通知" else "Reminders",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            ToggleConfigRow(
                title = if (isJp) "デイリーワークアウト通知" else "Daily Workout Alerts",
                desc = if (isJp) "毎日設定された時間にモチベーション促すメッセージを通知" else "Remind me to execute the scientific cycles daily",
                checked = enabled,
                onCheckChange = { onRemindToggle() }
            )

            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlarmClick() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (isJp) "アラーム時間設定" else "Target Alert Time", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (isJp) "この時間に毎日速歩を催促します" else "Taps to modify the reminder schedules", fontSize = 10.sp, color = TextMutedGrey)
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DangerZoneCard(isJp: Boolean, onClearAll: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = if (isJp) "ワークアウト履歴を削除しますか？" else "Delete workout history?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isJp) 
                        "すべてのログに記録されたセッションが完全に削除されます。この操作は取り消せません。" 
                    else 
                        "This permanently removes all logged sessions. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showConfirmDialog = false
                    }
                ) {
                    Text(
                        text = if (isJp) "削除" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(
                        text = if (isJp) "キャンセル" else "Cancel",
                        color = TextMutedGrey
                    )
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isJp) "危険領域" else "DANGER ZONE",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isJp) 
                    "全てのパーソナル履歴、サイクル歩数ログを端末から消去します。復元はできません。" 
                else 
                    "Permanently delete all workout history. This will clear all your session logs from this device.",
                fontSize = 11.sp,
                color = TextMutedGrey,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = if (isJp) "履歴を消去する" else "Delete Workout History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}


// ==========================================
// CORE HUD: ACTIVE RUN OVERLAY (NIKE RUN DIRECT DNA)
// ==========================================
@Composable
fun TrackingHudView(
    state: WalkingForegroundService.ServiceState.Active,
    viewModel: WalkingViewModel,
    isJp: Boolean
) {
    val context = LocalContext.current
    val voiceEnabled by viewModel.isVoiceEnabled.collectAsStateWithLifecycle()
    val totalInPhase = state.phaseDurationTotalSeconds
    val timeLeft = state.timeLeftInPhaseSeconds
    val percentTimeFraction = if (totalInPhase > 0) timeLeft.toFloat() / totalInPhase.toFloat() else 1f

    val isPhaseFast = state.currentPhase == WalkingForegroundService.Phase.FAST
    val phaseColorAccent = if (isPhaseFast) LaserCrimson else MaterialTheme.colorScheme.secondary
    val phaseThemeBg = if (isPhaseFast) Color(0xFF1E0B0F) else Color(0xFF071C22)

    val displayedName = if (isPhaseFast) {
        if (isJp) "早歩き" else "FAST WALK"
    } else {
        if (isJp) "ゆっくり歩き" else "SLOW RECOVERY"
    }

    val actionHintText = if (isPhaseFast) {
        if (isJp) "全力を振り絞って大きく腕を振ろう！" else "MAX EFFORT · SWING ARMS BACKWARD!"
    } else {
        if (isJp) "呼吸を整えてリラックス歩き" else "DEEP NASAL BREATH · EASY STEPS"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(phaseThemeBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hud top meta details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isJp) "信州大インターバル歩走公式" else "JAPANESE INTERVAL WALKING",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = TextMutedGrey,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Cycle ${state.currentCycle} / ${state.totalCycles}",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TextOnObsidian
                )
            }

            // Beautiful status pill
            val runtimeBg = if (state.isRunning) {
                SystemSuccess.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(runtimeBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (state.isRunning) {
                        if (isJp) "追跡中" else "LIVE TRACKING"
                    } else {
                        if (isJp) "一時停止中" else "PAUSED"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    color = if (state.isRunning) SystemSuccess else TextMutedGrey
                )
            }
        }

        // Giant Concentric Interval Circle Head (Nike / Adidas style)
        Box(
            modifier = Modifier
                .size(260.dp)
                .drawBehind {
                    // Back glow ring
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.4f),
                        radius = size.width / 2f
                    )
                    // Structural Ring
                    drawCircle(
                        color = BorderCarbon,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                        radius = (size.width - 16.dp.toPx()) / 2f
                    )
                    // Active Sprint / Recovery running ring
                    drawArc(
                        color = phaseColorAccent,
                        startAngle = -90f,
                        sweepAngle = 360f * percentTimeFraction,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                        size = size.copy(
                            width = size.width - 16.dp.toPx(),
                            height = size.height - 16.dp.toPx()
                        ),
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Large numeral clock timer
                Text(
                    text = String.format(Locale.getDefault(), "%d:%02d", timeLeft / 60, timeLeft % 60),
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-2).sp,
                    color = TextOnObsidian
                )
                Text(
                    text = displayedName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = phaseColorAccent,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (voiceEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Voice Prompt Active badge",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isJp) "音声ガイド有効" else "VOICE PROMPT ACTIVE",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Running statistics telemetry
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = actionHintText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = phaseColorAccent,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HudTelemetryMetric(
                        label = if (isJp) "現在の歩数" else "STEPS TRACKED",
                        value = "${state.steps}",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    HudTelemetryMetric(
                        label = if (isJp) "ケイデンス (歩/分)" else "CADENCE SPM",
                        value = "${state.cadence}",
                        accentColor = TextOnObsidian
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderCarbon, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HudTelemetryMetric(
                        label = if (isJp) "カロリー (kcal)" else "CAL BURNED",
                        value = String.format(Locale.getDefault(), "%.1f", state.calories),
                        accentColor = LaserCrimson
                    )
                    HudTelemetryMetric(
                        label = if (isJp) "推定ペース (分/km)" else "PACE ESTIMATE",
                        value = String.format(Locale.getDefault(), "%.1f", state.pace),
                        accentColor = RecoveryTeal
                    )
                }
            }
        }

        // Overall progress tracker
        Column(modifier = Modifier.fillMaxWidth()) {
            val totalSecExpected = state.totalCycles * (WalkingForegroundService.slowDurationMinutes + WalkingForegroundService.fastDurationMinutes) * 60f
            val elapsedSoFar = state.elapsedTotalSeconds.toFloat()
            val totalSessionPct = if (totalSecExpected > 0) (elapsedSoFar / totalSecExpected).coerceIn(0f, 1f) else 1f
            
            val elapsedMin = state.elapsedTotalSeconds / 60
            val elapsedSec = state.elapsedTotalSeconds % 60
            val totalMin = totalSecExpected.toInt() / 60
            val totalSec = totalSecExpected.toInt() % 60
            val timerStr = String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", elapsedMin, elapsedSec, totalMin, totalSec)
            
            val targetMinutesRounded = (totalSecExpected / 60f).toInt()
            val progressText = if (isJp) {
                "セッション進捗: ${String.format(Locale.getDefault(), "%02d:%02d", elapsedMin, elapsedSec)} （目標 ${targetMinutesRounded}分）"
            } else {
                "SESSION PROGRESS: ${String.format(Locale.getDefault(), "%02d:%02d", elapsedMin, elapsedSec)} (OUT OF ${targetMinutesRounded} MINS)"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progressText, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = TextMutedGrey
                )
                Text(text = "${(totalSessionPct * 100f).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = totalSessionPct,
                color = MaterialTheme.colorScheme.primary,
                trackColor = BorderCarbon,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 4.dp)
            )
        }

        // Workout navigation controls rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // STOP ACTION
            IconButton(
                onClick = { viewModel.stopWorkout() },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF331114))
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "stop workout", tint = LaserCrimson, modifier = Modifier.size(24.dp))
            }

            // PRIMARY PLAY PAUSE ROTATING BUTTON
            Button(
                onClick = { 
                    if (state.isRunning) {
                        viewModel.pauseWorkout()
                    } else {
                        viewModel.resumeWorkout()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = phaseColorAccent,
                    contentColor = Color.Black
                ),
                shape = CircleShape,
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, 
                    contentDescription = "playpause",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            // SKIP RECOVERY ACCELERATOR ACTION
            IconButton(
                onClick = { viewModel.skipIntervalPhase() },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16252C))
            ) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "skip interval", tint = RecoveryTeal, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun HudTelemetryMetric(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey, letterSpacing = 0.5.sp)
        Text(
            text = value,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = accentColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun SessionCompletionScreen(
    data: com.example.service.CompletedSessionData,
    isJp: Boolean,
    onGoToDashboard: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotateAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071114)) // Elegant obsidian-emerald gym-tech dark background
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Large celebration checkmark with glowing concentric circles
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    // Glowing outer ring radiating success aura
                    drawCircle(
                        color = SystemSuccess.copy(alpha = 0.15f * scale),
                        radius = this.size.width / 2f
                    )
                    drawCircle(
                        color = SystemSuccess.copy(alpha = 0.3f),
                        radius = this.size.width / 2.5f
                    )
                }
        ) {
            // Draw floating confetti around checkmark
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val angles = listOf(30f, 75f, 120f, 165f, 210f, 255f, 300f, 345f)
                val colors = listOf(SystemSuccess, RecoveryTeal, LaserCrimson, Color.Yellow, Color.Cyan)
                
                angles.forEachIndexed { i, baseAngle ->
                    val angleRad = Math.toRadians((baseAngle + rotateAnimation).toDouble())
                    val distance = (this.size.width / 2.1f) + (10f * scale)
                    val x = center.x + (distance * Math.cos(angleRad).toFloat())
                    val y = center.y + (distance * Math.sin(angleRad).toFloat())
                    
                    drawCircle(
                        color = colors[i % colors.size].copy(alpha = 0.8f),
                        radius = 4f * density,
                        center = Offset(x, y)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = SystemSuccess,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }

        Text(
            text = if (isJp) "トレーニング完了！" else "Workout Complete!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = SystemSuccess,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (isJp) 
                "素晴らしい運動効率です。30分間のインターバル効果にしっかりと繋がっています！" 
            else 
                "Incredible effort! You completed your science-backed active intervals.",
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main modern report layout in a beautiful card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isJp) "セッション統計" else "SESSION METRICS SUMMARY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 1.dp)

                // Grid layout with 4 metrics
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        CompletionMetricItem(
                            icon = Icons.Default.DirectionsWalk,
                            label = if (isJp) "総歩数" else "STEPS COUNT",
                            value = "${data.steps}",
                            unit = if (isJp) " 歩" else " steps",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        CompletionMetricItem(
                            icon = Icons.Default.Whatshot,
                            label = if (isJp) "消費カロリー" else "CALORIES BURNED",
                            value = String.format(Locale.getDefault(), "%.1f", data.calories),
                            unit = " kcal",
                            color = LaserCrimson
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        val min = data.durationSeconds / 60
                        val sec = data.durationSeconds % 60
                        CompletionMetricItem(
                            icon = Icons.Default.Timer,
                            label = if (isJp) "運動時間" else "TOTAL ACTIVE DURATION",
                            value = String.format(Locale.getDefault(), "%d:%02d", min, sec),
                            unit = "",
                            color = TextOnObsidian
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        CompletionMetricItem(
                            icon = Icons.Default.Speed,
                            label = if (isJp) "推定ペース" else "ESTIMATED PACE",
                            value = String.format(Locale.getDefault(), "%.1f", data.avgPace),
                            unit = " m/km",
                            color = RecoveryTeal
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 1.dp)

                // Extra details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isJp) "完了サイクル" else "Cycles Saved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMutedGrey
                        )
                    }
                    Text(
                        text = "Cycle ${data.totalCycles}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = TextOnObsidian,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isJp) "平均ケイデンス" else "Avg Cadence",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMutedGrey
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f SPM", data.avgCadence),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextOnObsidian
                    )
                }

                val formattedTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(data.dateMillis))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isJp) "記録日時" else "Completed At",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMutedGrey
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMutedGrey
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Go to dashboard CTA Button
        Button(
            onClick = onGoToDashboard,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isJp) "ダッシュボードに戻る" else "Go to Dashboard",
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun CompletionMetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextMutedGrey,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = color,
                lineHeight = 28.sp
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMutedGrey,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                )
            }
        }
    }
}
