package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WalkingViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: WalkingViewModel,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isJp by viewModel.isJpLanguage.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background == CarbonBlack

    // Saved weight if any to prefill
    val savedWeight by viewModel.onboardingWeight.collectAsStateWithLifecycle()
    val weeklyGoal by viewModel.weeklyWalkGoal.collectAsStateWithLifecycle()

    // Slide steps tracking
    var currentStep by rememberSaveable { mutableStateOf(0) }
    val totalSteps = 8 // 0 = Welcome, 1 = Steps, 2 = Insights, 3 = Calorie, 4 = Weight, 5 = Weekly Goal, 6 = Permission, 7 = Ready

    // Trigger starting analytics once
    LaunchedEffect(Unit) {
        viewModel.logOnboardingEvent("onboarding_started")
    }

    // Checking permissions live
    var hasActivityPermission by remember { mutableStateOf(false) }
    var hasNotifyPermission by remember { mutableStateOf(false) }
    val hasHcPermission by viewModel.hasHealthConnectPermissions.collectAsStateWithLifecycle()

    fun updatePermissionStates() {
        hasActivityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.ACTIVITY_RECOGNITION"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasNotifyPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    LaunchedEffect(currentStep) {
        updatePermissionStates()
    }

    // Permission launch tracker
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStates()
        val granted = results.values.all { it }
        viewModel.logOnboardingEvent(
            if (granted) "permission_granted" else "permission_denied",
            mapOf("permissions" to results.keys.joinToString())
        )
    }

    // Stably declared Health Connect launcher at top-level
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.d("HealthConnect", "Onboarding Health Connect permissions request finished: granted: $granted")
        viewModel.checkHealthConnectPermissions()
        viewModel.logOnboardingEvent(
            if (granted.isNotEmpty()) "health_connect_connected" else "permission_denied"
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            OnboardingNavigationRow(
                currentStep = currentStep,
                totalSteps = totalSteps,
                isJp = isJp,
                onBack = {
                    if (currentStep > 0) {
                        currentStep--
                    }
                },
                onSkip = {
                    viewModel.logOnboardingEvent("onboarding_skipped")
                    // Jump directly to screen 4 (Weight collection) as required
                    currentStep = 4
                },
                onContinue = {
                    if (currentStep < totalSteps - 1) {
                        currentStep++
                    } else {
                        // Finish onboarding completely
                        viewModel.setOnboardingCompleted(true)
                        onOnboardingFinished()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    if (isDark) {
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF162512), CarbonBlack),
                            radius = 1200f
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFE5F5DD), MinimalLightBg),
                            radius = 1200f
                        )
                    }
                )
        ) {
            // Animated Header Progress Bar
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )

            // Animated Screen Crossfade Slide
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width / 2 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width / 2 } + fadeOut()
                        )
                    }
                },
                label = "OnboardingPageAnimation"
            ) { step ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    when (step) {
                        0 -> WelcomeSlide(isJp)
                        1 -> StepTrackingSlide(isJp)
                        2 -> WalkingInsightsSlide(isJp)
                        3 -> CaloriesSlide(isJp)
                        4 -> WeightCollectionSlide(
                            isJp = isJp,
                            initialWeight = savedWeight,
                            onWeightSaved = { weight ->
                                viewModel.setOnboardingWeight(weight)
                            }
                        )
                        5 -> WeeklyGoalCollectionSlide(
                            isJp = isJp,
                            viewModel = viewModel
                        )
                        6 -> PermissionsSlide(
                            isJp = isJp,
                            viewModel = viewModel,
                            hasActivity = hasActivityPermission,
                            hasNotify = hasNotifyPermission,
                            hasHc = hasHcPermission,
                            onRequest = {
                                viewModel.logOnboardingEvent("permission_requested")
                                val list = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    list.add("android.permission.ACTIVITY_RECOGNITION")
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    list.add("android.permission.POST_NOTIFICATIONS")
                                }
                                if (list.isNotEmpty()) {
                                    permissionLauncher.launch(list.toTypedArray())
                                }
                            },
                            onConnectHc = {
                                android.util.Log.d("HealthConnect", "Onboarding permissions slide Connect button clicked")
                                viewModel.healthConnectPermissionManager.launchPermissionRequestSafely(hcPermissionLauncher)
                            }
                        )
                        7 -> CompletionReadySlide(isJp, savedWeight, hasActivityPermission, hasHcPermission, weeklyGoal)
                    }
                }
            }
        }
    }
}

// ==========================================
// ONBOARDING NAVIGATION BAR (DYNAMIC BUTTON CONTROLS)
// ==========================================
@Composable
fun OnboardingNavigationRow(
    currentStep: Int,
    totalSteps: Int,
    isJp: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Button: Back (Disabled on Welcome Screen)
            if (currentStep > 0 && currentStep < totalSteps - 1) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back indicator button",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Middle Column: Progress Text Tick Indicator
            Text(
                text = "${currentStep + 1} / $totalSteps",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMutedGrey
            )

            // Right Button: Continue, Finish, or Skip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Skip Button: Page 0 to 3 only (Required by prompt)
                if (currentStep in 0..3) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .height(48.dp)
                            .semantics { contentDescription = "Skip onboarding intro slides" }
                    ) {
                        Text(
                            text = if (isJp) "スキップ" else "SKIP",
                            fontWeight = FontWeight.Bold,
                            color = TextMutedGrey,
                            fontSize = 13.sp
                        )
                    }
                }

                // Continue / Finish Button
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val label = if (currentStep == totalSteps - 1) {
                            if (isJp) "トレーニング開始" else "START TRACKING"
                        } else {
                            if (isJp) "次へ" else "CONTINUE"
                        }
                        Text(
                            text = label,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Icon(
                            imageVector = if (currentStep == totalSteps - 1) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: WELCOME SLIDE
// ==========================================
@Composable
fun WelcomeSlide(isJp: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Welcome Premium Dynamic Sport Track Canvas Illustration
        Box(
            modifier = Modifier
                .size(160.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val strokeColor = MaterialTheme.colorScheme.primary
            val recoveryColor = MaterialTheme.colorScheme.secondary
            val pulseState = rememberInfiniteTransition(label = "pulseWelcome")
            val angle by pulseState.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "welcomeRot"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw dynamic concentric circles with gaps simulating dynamic walking pace
                drawCircle(
                    color = strokeColor.copy(alpha = 0.1f),
                    radius = size.minDimension / 2.2f,
                    style = Stroke(width = 8.dp.toPx())
                )
                drawArc(
                    color = strokeColor,
                    startAngle = angle,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = recoveryColor,
                    startAngle = angle + 180f,
                    sweepAngle = 80f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) "JIWへようこそ" else "Welcome to JIW",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "[Japanese Interval Walking]",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grid-based beautifully spaced list of hero features
        Text(
            text = if (isJp) {
                "ジムに行く時間がない？ 毎日1万歩も歩けない？ JIW（インターバル速歩）なら、毎日たったの30分歩くだけで最高に健康的で引き締まった身体を実感できます。ゆっくりと早歩きを繰り返すだけのお手軽設計です！"
            } else {
                "No gym? No time for 10,000 steps a day? JIW matches your busy life by giving you all the benefits in just 30 minutes a day. Simply alternate between relaxed and brisk walking!"
            },
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val features = if (isJp) listOf(
                Triple(Icons.Default.Speed, "かんたん歩数アシスト", "早歩きとゆっくり歩きのタイミングをアプリがやさしく教えます"),
                Triple(Icons.Default.DirectionsWalk, "らくらく自動計測", "スマートフォンを入れたままで、あなたの歩む速さを自動計測"),
                Triple(Icons.Default.Whatshot, "納得の消費カロリー", "家事や通勤などの日常的なお散歩のエネルギー燃焼を表示"),
                Triple(Icons.Default.Sync, "健康アプリと自動連携", "Google Fitやスマホ内臓の健康管理アプリと自動で同期できます")
            ) else listOf(
                Triple(Icons.Default.Speed, "Warm Interval Guides", "We gently guide you exactly when to speed up and slow down"),
                Triple(Icons.Default.DirectionsWalk, "Effortless Step Tracker", "Tracks your daily walks automatically right from your pocket"),
                Triple(Icons.Default.Whatshot, "Realistic Calorie Burn", "See how much fat you burn during everyday tasks and walks"),
                Triple(Icons.Default.Sync, "Auto Sync", "Connects securely with Google Fit or Health Connect in one click")
            )

            features.forEach { (icon, title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = desc, fontSize = 11.sp, color = TextMutedGrey)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: STEP TRACKING SLIDE
// ==========================================
@Composable
fun StepTrackingSlide(isJp: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackingTrans = rememberInfiniteTransition(label = "stepAnim")
            val radiusScale by trackingTrans.animateFloat(
                initialValue = 40f,
                targetValue = 65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseOutQuad),
                    repeatMode = RepeatMode.Restart
                ),
                label = "stepRadius"
            )
            val opacityArc by trackingTrans.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseOutQuad),
                    repeatMode = RepeatMode.Restart
                ),
                label = "stepOpacity"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = SportsVolt.copy(alpha = opacityArc * 0.3f),
                    radius = radiusScale.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }

        Text(
            text = if (isJp) "毎日たった30分でOK！" else "Anytime, Anywhere",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "お買い物中、通勤中、あるいはお散歩。いつものちょっとした歩行がお手軽で効果的な有酸素トレーニングに早変わり。1日たった30分の習慣で十分です！"
            } else {
                "You don't need a gym membership or hours of free time. JIW is designed to turn your commute, errand runs, or casual strolls into premium exercise within just 30 minutes!"
            },
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isJp) "ポケットやバッグに入れたままで！" else "Pocket-Friendly Tracking",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isJp) {
                        "スマホを身につけておくだけでOK。電車や車の揺れなどの無駄な動きを自動で除き、本当に歩いた歩数だけをスマートに、バッテリーをほとんど消費せずにカウントします。"
                    } else {
                        "The app works quietly in the background, tracking only your genuine walking. It automatically ignores train or car vibrations, saving your phone's battery completely."
                    },
                    fontSize = 11.sp,
                    color = TextMutedGrey,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==========================================
// SCREEN 3: WALKING INSIGHTS SLIDE
// ==========================================
@Composable
fun WalkingInsightsSlide(isJp: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }

        Text(
            text = if (isJp) "自分のペースで進むだけ" else "Keep It Simple & Fun",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "難しいルールや計算は一切ありません。いつもの歩きに「少しだけ早歩き」を交ぜるだけで、アプリが今日のウォーキング結果を分かりやすくまとめてくれます。"
            } else {
                "You don't need to learn any complex rules. Just alternate easy walking with slightly faster steps. We automatically track how well you did!"
            },
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Dynamic visual elements representation
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val items = listOf(
                Triple(
                    Icons.Default.Speed,
                    if (isJp) "歩く早さ" else "Walking Pace",
                    if (isJp) "どれくらい早く歩く\nことができたか" else "How fast you walk throughout the day"
                ),
                Triple(
                    Icons.Default.HourglassEmpty,
                    if (isJp) "メリハリ歩行" else "Walk Switch",
                    if (isJp) "ゆっくりと早歩きの\n自動タイミング" else "Switching between slow and brisk steps"
                ),
                Triple(
                    Icons.Default.Map,
                    if (isJp) "歩行の記録" else "Logs & Paths",
                    if (isJp) "歩いた距離とお時間を\n優しく記録" else "Showing clean records of your walks"
                )
            )

            items.forEach { (icon, title, desc) ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            fontSize = 8.sp,
                            color = TextMutedGrey,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: CALORIES SLIDE
// ==========================================
@Composable
fun CaloriesSlide(isJp: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = LaserCrimson,
                modifier = Modifier.size(60.dp)
            )
        }

        Text(
            text = if (isJp) "賢くカロリー消費！" else "Burn Calories Efficiently",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "少し息がはずむ「早歩き」を普段の生活に取り入れるだけで、カロリー消費効率がアップ。あなたに合わせたエネルギー計算を自動で行います。"
            } else {
                "Whenever you walk briskly during your day, your body burns extra calories. We make it easy to see how much energy you burn without any manual entry!"
            },
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val bullets = if (isJp) listOf(
                    Triple(Icons.Default.Scale, "あなた専用の計算", "現在の体重を入力することで、歩行パターンに合わせたより確かなカロリーを表示できます。"),
                    Triple(Icons.Default.Speed, "二つの歩く速さ", "ゆっくり歩いている時と、早く歩いている時の違いをかしこく見分けて計算します。"),
                    Triple(Icons.Default.Info, "楽しく続けるために", "表示される数値は、毎日の健康づくりとやる気を楽しく高めるための優しい目標目安です。")
                ) else listOf(
                    Triple(Icons.Default.Scale, "Weight settings", "Entering your weight helps customize your calorie burn measurements automatically."),
                    Triple(Icons.Default.Speed, "Speed recognition", "We comfortably separate fast walking from easy strolls to measure your effort."),
                    Triple(Icons.Default.Info, "Supportive guide", "Enjoy watching your counts grow as a friendly way to stay active and feel energetic!")
                )

                bullets.forEach { (icon, title, desc) ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 8.dp)
                                .size(14.dp)
                        )
                        Column {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = desc,
                                fontSize = 10.sp,
                                color = TextMutedGrey,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: WEIGHT COLLECTION SLIDE
// ==========================================
@Composable
fun WeightCollectionSlide(
    isJp: Boolean,
    initialWeight: Float?,
    onWeightSaved: (Float) -> Unit
) {
    var weightInput by remember { mutableStateOf(initialWeight?.toString() ?: "") }
    var isError by remember { mutableStateOf(false) }

    // Validation limits
    val minW = 20.0f
    val maxW = 300.0f

    val errorText = if (isJp) {
        "体重は ${minW.toInt()}kg から ${maxW.toInt()}kg の間で入力してください。"
    } else {
        "Weight must be between ${minW.toInt()} and ${maxW.toInt()} kg."
    }

    // Trigger saving when correct
    fun handleWeightInput(input: String) {
        weightInput = input
        val parsed = input.toFloatOrNull()
        if (parsed != null && parsed in minW..maxW) {
            isError = false
            onWeightSaved(parsed)
        } else {
            isError = true
        }
    }

    // Init pre-load
    LaunchedEffect(initialWeight) {
        if (initialWeight != null && weightInput.isEmpty()) {
            weightInput = initialWeight.toString()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Scale,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) "かんたん体重の設定" else "Let's Get Personalized",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isJp) {
                "あなたにぴったりの消費カロリーを計算するため、現在の体重を入力してください（いつでも後から変更できます）。この数値はスマホの中だけに安全に保存されます。"
            } else {
                "Please enter your weight below to help us estimate your calorie burns accurately during your 30-minute walks. Your weight is saved safely only on your phone."
            },
            fontSize = 12.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = weightInput,
            onValueChange = { handleWeightInput(it) },
            label = { Text(if (isJp) "体重 (kg)" else "Body Weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            isError = isError,
            placeholder = { Text("70.0") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = LaserCrimson
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isError) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = LaserCrimson, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = errorText,
                    color = LaserCrimson,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (weightInput.toFloatOrNull() != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SystemSuccess, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isJp) "入力されました！ありがとうございます" else "Weight saved successfully",
                    color = SystemSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = SystemWarning, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isJp) "先に進むには、正しい体重を入力してください（20kg〜300kg）" else "Please enter a valid weight to continue (20kg to 300kg)",
                    color = SystemWarning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// SCREEN 6: PERMISSIONS SLIDE
// ==========================================
@Composable
fun PermissionsSlide(
    isJp: Boolean,
    viewModel: WalkingViewModel,
    hasActivity: Boolean,
    hasNotify: Boolean,
    hasHc: Boolean,
    onRequest: () -> Unit,
    onConnectHc: () -> Unit
) {
    val sdkStatus = remember { viewModel.healthConnectManager.getSdkStatus() }
    val isHcAvailable = sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.SafetyCheck,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) "かんたんアプリの準備" else "Let's Get Set Up!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isJp) {
                "スマートフォンをポケットに入れたままでも、歩数を正しくカウントして「早歩き・ゆっくり」の切り替え指示をお伝えするために、2つの許可をオンにしてください。"
            } else {
                "To let JIW automatically count your steps in your pocket and point out exactly when to walk fast or slow, we just need a few quick settings."
            },
            fontSize = 12.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Grid-like Permissions blocks
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Permission 1: ACTIVITY_RECOGNITION
            PermissionStatusRow(
                title = if (isJp) "歩数の計測 (必須)" else "Count My Steps (Required)",
                desc = if (isJp) "歩いている時に、スマホのセンサーから歩数を自動で優しくカウントするために必要です。" else "Used to measure your steps automatically right when you start walking.",
                granted = hasActivity,
                imageVector = Icons.Default.DirectionsWalk
            )

            // Permission 2: POST_NOTIFICATIONS
            PermissionStatusRow(
                title = if (isJp) "切り替えの合図 (推奨)" else "Healthy Guide Notes (Recommended)",
                desc = if (isJp) "ゆっくり歩きから早歩きに変わる時の心地よいベル音や振動、大切なリマインドに使います。" else "We drop a quick nudge when it's time to speed up or take a slow recovery breath.",
                granted = hasNotify,
                imageVector = Icons.Default.NotificationsActive
            )

            // Permission 3: HEALTH CONNECT (Optional Integration)
            if (isHcAvailable) {
                PermissionStatusRow(
                    title = if (isJp) "他の健康アプリとつなぐ (任意)" else "Share with Health Apps (Optional)",
                    desc = if (isJp) "Google Fitやスマホ内蔵の健康管理アプリと、歩数データなどを自動でかんたんに共有します。" else "Import and export burned calories and walking logs between JIW and other fitness tools.",
                    granted = hasHc,
                    imageVector = Icons.Default.Sync,
                    showConnectButton = true,
                    onConnectClick = onConnectHc
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasActivity) {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isJp) "設定画面で許可する" else "GRANT PERMISSION",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = SystemSuccess.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, SystemSuccess.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SystemSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isJp) "基本的な歩行計測の準備ができています" else "Ready to track your daily walks!",
                        color = SystemSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    desc: String,
    granted: Boolean,
    imageVector: ImageVector,
    showConnectButton: Boolean = false,
    onConnectClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (granted) SystemSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (granted) SystemSuccess else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, fontSize = 10.sp, color = TextMutedGrey, lineHeight = 13.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted token tick",
                    tint = SystemSuccess,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                if (showConnectButton) {
                    Button(
                        onClick = onConnectClick,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(text = "Connect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not granted caution sign",
                        tint = SystemWarning,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 6: WEEKLY GOAL SELECTOR SLIDE
// ==========================================
@Composable
fun WeeklyGoalCollectionSlide(
    isJp: Boolean,
    viewModel: WalkingViewModel
) {
    val weeklyGoal by viewModel.weeklyWalkGoal.collectAsStateWithLifecycle()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) "週間目標の設定" else "Weekly Walking Goal",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isJp) {
                "一週間に何回インターバル速歩を行うか設定します（月曜日〜日曜日）。最初は週に5回を目安にするのがおすすめです！"
            } else {
                "Select how many walks per week you want to do (Monday to Sunday). We recommend starting with 5 walks/week to build a healthy scientific habit."
            },
            fontSize = 12.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Minus button
            IconButton(
                onClick = {
                    if (weeklyGoal > 1) {
                        viewModel.setWeeklyWalkGoal(weeklyGoal - 1)
                    }
                },
                enabled = weeklyGoal > 1,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (weeklyGoal > 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "decrease goal",
                    tint = if (weeklyGoal > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$weeklyGoal",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isJp) "回 / 週" else "walks / week",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMutedGrey
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Plus button
            IconButton(
                onClick = {
                    if (weeklyGoal < 7) {
                        viewModel.setWeeklyWalkGoal(weeklyGoal + 1)
                    }
                },
                enabled = weeklyGoal < 7,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (weeklyGoal < 7) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "increase goal",
                    tint = if (weeklyGoal < 7) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ==========================================
// SCREEN 7: COMPLETION READY SLIDE
// ==========================================
@Composable
fun CompletionReadySlide(
    isJp: Boolean,
    savedWeight: Float?,
    hasActivity: Boolean,
    hasHc: Boolean,
    weeklyGoal: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val checkAnim = rememberInfiniteTransition(label = "checkPulse")
            val sizeScale by checkAnim.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "checkSize"
            )

            Icon(
                imageVector = Icons.Default.OfflinePin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp * sizeScale)
            )
        }

        Text(
            text = if (isJp) "さあ、始めましょう！" else "You're Ready!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "準備はすべて完了しました！ジムに通う時間がなくても、1日たった30分の「メリハリ歩き」が毎日の体調をベストに整えます。さっそく今日から心地よい第一歩を踏み出してみましょう！"
            } else {
                "You're all set! No need for the gym or hours of free time. Just 30 minutes of natural, smart walking during your day is all it takes to keep you healthy and energized. Let's take that first step together!"
            },
            fontSize = 13.sp,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // High fidelity summary card with ticks
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isJp) "【現在の設定まとめ】" else "YOUR SETUP SUMMARY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Item 1: Weight Configured
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isJp) "体重の登録完了: ${savedWeight ?: 70f} kg" else "Weight saved: ${savedWeight ?: 70f} kg",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Item 2: Core Permissions Obtained
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasActivity) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (hasActivity) Color.Green else LaserCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (hasActivity) {
                            if (isJp) "お散歩・ウォーキング自動計測: 準備完了" else "Auto step counting: Ready"
                        } else {
                            if (isJp) "お散歩計測: 未許可 (制限あり)" else "Auto step counting: Tap to grant"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Item 3: Health Connect Optional Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasHc) Icons.Default.Check else Icons.Default.Watch,
                        contentDescription = null,
                        tint = if (hasHc) Color.Green else TextMutedGrey,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (hasHc) {
                            if (isJp) "他の健康アプリとの連携: 接続中 (自動保存)" else "Other health apps: Connected"
                        } else {
                            if (isJp) "他の健康アプリとの連携: 未接続 (後で追加できます)" else "Other health apps: Skip for now"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Item 4: Weekly Walk Goal Summary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isJp) "週間ウォーキング目標: 週に ${weeklyGoal} 回の速歩" else "Weekly walking goal: $weeklyGoal walks / week",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
