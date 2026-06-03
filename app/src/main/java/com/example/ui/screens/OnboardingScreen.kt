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

    // Slide steps tracking
    var currentStep by rememberSaveable { mutableStateOf(0) }
    val totalSteps = 7 // 0 = Welcome, 1 = Steps, 2 = Insights, 3 = Calorie, 4 = Weight, 5 = Permission, 6 = Ready

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
                        Brush.verticalGradient(
                            colors = listOf(MinimalLightBg, Color(0xFFF0F7EC))
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
                        .padding(24.dp),
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
                        5 -> PermissionsSlide(
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
                            }
                        )
                        6 -> CompletionReadySlide(isJp, savedWeight, hasActivityPermission, hasHcPermission)
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
                "世界的な研究成果に基づく「インターバル速歩」を科学的に実践するためのアプリです。運動効果を大きく高め、確実なフィットネス成果を引き出します。"
            } else {
                "Scientific interval training engineered around Dr. Hiroshi Nose's medical proofs. Alternate slow walks and fast strolls to amplify aerobic capacity."
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
                "⚡ スマート自動歩行トラッカー" to "歩行を検知して強弱周期をアシストします",
                "⏱️ ピッチ・ケイデンス解析" to "分あたりの歩数をリアルタイム測定",
                "🔥 正確なサイエンス消費カロリー" to "体重係数を用いたMETs公式エスティメイト",
                "🧬 ヘルスコネクト自動連携" to "Google Fitほか健診アプリに歩数を双方向同期"
            ) else listOf(
                "⚡ Smart Step Tracking" to "Automatically segment walking routines with precision",
                "⏱️ Cadence / Pace Tracking" to "Monitor stepped speeds and dynamic velocity intervals",
                "🔥 Calorie Burn METs Engine" to "Dynamic energy calculations customized directly to your body mass",
                "🧬 Health Connect Integration" to "Sync walked records instantly with persistent device databases"
            )

            features.forEach { (title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
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
            text = if (isJp) "高精度ハードウェア計測" else "Dynamic Step Trackers",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "Android内蔵の専用ハードウェア歩数カウンターを採用し、バッテリー消費を極限まで抑えます。"
            } else {
                "Employs Android's low-power hardware step sensors to capture step counts, minimizing screen-off battery drainage."
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
                        text = if (isJp) "ファントムカウントを防止" else "Anti-Phantom Validations",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isJp) {
                        "腕振りや車の揺れなど、無効な動きや「偽の歩数」を高度な活動バリデーションでフィルタリング。本当に歩いている時の運動ログのみを厳密に計上します。"
                    } else {
                        "Filters out phantom steps caused by arm waving or vehicle vibrations. Dynamic walking validations guarantee logs consist purely of genuine physical locomotion."
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
            text = if (isJp) "歩行バイオメカニクス分析" else "Locomotion Insights",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "ただの歩数計算ではありません。あなたの歩行サイクルに含まれる速度成分を定量化します。"
            } else {
                "Far beyond static integers. We dynamically decompose pacing attributes behind every stride to score walking health."
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
                    if (isJp) "ケイデンス" else "Cadence",
                    if (isJp) "歩行周期の周波数\n(SPM)" else "Frequency (Steps/Min)"
                ),
                Triple(
                    Icons.Default.HourglassEmpty,
                    if (isJp) "強弱切替" else "Intervals",
                    if (isJp) "ゆっくりと早歩き\nの交互ピッチ" else "Fast vs Slow gait toggles"
                ),
                Triple(
                    Icons.Default.Map,
                    if (isJp) "距離・ペース" else "Distance",
                    if (isJp) "累積歩数から\n距離・速度を算出" else "Scientific strides metrics"
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
            text = if (isJp) "科学的消費エネルギー" else "Caloric Expenditures",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "インターバル速歩中の運動負荷に基づき、消費カロリーを精密に計算します。"
            } else {
                "Dynamically approximates energy burned during high key interval segments."
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
                val bulletPoints = if (isJp) listOf(
                    "⚖️ 体重がカロリーの精度に直結：体重入力によって、カロリー算出アルゴリズムが最適化されます。",
                    "📐 科学的なMETs係数：ゆっくり歩行と強歩行それぞれの負荷を区別し、METsエネルギー公式を用いて合算。",
                    "⚠️ あくまで目安として：表示される数値はフィットネス向上のための推定値であり、医療目的には使用できません。"
                ) else listOf(
                    "⚖️ Weight calibration is central: Entering precise mass figures provides accurate biomechanical formulas.",
                    "📐 Dynamic METs metrics: Alternates energy equations based on gait speed intervals and active cadences.",
                    "⚠️ Estimated figures: Calories are strictly physiological estimates, built as fitness scoring guides rather than diagnostic models."
                )

                bulletPoints.forEach { point ->
                    Text(
                        text = point,
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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
            text = if (isJp) "プロフィール体重設定" else "Calibrate Body Weight",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isJp) {
                "METs（メッツ）を用いた高精度な消費カロリー計算のために、現在の体重を入力してください。数値は端末内に安全に暗号化保存されます。"
            } else {
                "Precision tracking equation (MET formula) requires physical mass coefficients. Weight is stored strictly locally."
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
            Text(
                text = errorText,
                color = LaserCrimson,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
        } else if (weightInput.toFloatOrNull() != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isJp) "✅ 適正な体重値が入力されました" else "✅ Valid weight parameters updated",
                color = Color.Green,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isJp) "⚠️ 先に進むには正しい体重を入力してください（20kg〜300kg）" else "⚠️ Valid bodyweight required to unlock step tracking (20kg-300kg)",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Start)
            )
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
    onRequest: () -> Unit
) {
    val sdkStatus = remember { viewModel.healthConnectManager.getSdkStatus() }
    val isHcAvailable = sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkHealthConnectPermissions()
        viewModel.logOnboardingEvent(
            if (granted.isNotEmpty()) "health_connect_connected" else "permission_denied"
        )
    }

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
            text = if (isJp) "トラッキング権限の管理" else "Grant Health Permissions",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isJp) {
                "バックグラウンドでの歩幅歩数カウント、インターバル切り替え検知を正常におこなうため、いくつかの基本権限が必要です。"
            } else {
                "Requires crucial system privileges to record passive cadences and guide transitions background."
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
                title = if (isJp) "💡 身体活動の認知検出 (必須)" else "💡 Physical Activity (Required)",
                desc = if (isJp) "スマホ内蔵のハードウェア歩数カウンターの数値を検知するのに必要です。" else "Used solely to retrieve real-time hardware footstep counters.",
                granted = hasActivity
            )

            // Permission 2: POST_NOTIFICATIONS
            PermissionStatusRow(
                title = if (isJp) "🔔 通知の許可 (推奨)" else "🔔 Post Notifications (Recommended)",
                desc = if (isJp) "ウォーク中の早歩き・ゆっくり交代の指示や、日々のリマインドを表示します。" else "Used to guide interval transition steps and active alarms.",
                granted = hasNotify
            )

            // Permission 3: HEALTH CONNECT (Optional Integration)
            if (isHcAvailable) {
                PermissionStatusRow(
                    title = if (isJp) "🧬 ヘルスコネクト (任意連携)" else "🧬 Health Connect Integration (Optional)",
                    desc = if (isJp) "Google Fitほか各種健康管理アプリと本アプリの歩行ログ・カロリーを双方向同期します。" else "Directly sync stepped sessions background with personal Fit accounts.",
                    granted = hasHc,
                    showConnectButton = true,
                    onConnectClick = {
                        requestPermissionsLauncher.launch(viewModel.healthConnectManager.requiredPermissions)
                    }
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
                    text = if (isJp) "システム認証をリクエスト" else "AUTHORIZE DETECTIONS",
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 12.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1B12))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isJp) "✅ トラッキングに必要な基本権限は承認されています" else "✅ Locomotion permissions authorized",
                    color = Color.Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    desc: String,
    granted: Boolean,
    showConnectButton: Boolean = false,
    onConnectClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (granted) Color.Green.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                if (showConnectButton) {
                    Button(
                        onClick = onConnectClick,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.Black),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(text = "Connect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not granted caution sign",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    hasHc: Boolean
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
            text = if (isJp) "歩こう、準備はできた！" else "You're Ready to Walk",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isJp) {
                "オンボーディングツアーと初期セッティングは無事に完了しました！サイエンスインターバル歩行で最高の結果を獲得しましょう。"
            } else {
                "Initial profile settings compiled and localized securely. You are set to optimize your calorie burns."
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
                    text = if (isJp) "【初期状態サマリー】" else "STATUS SETUP LOGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Item 1: Weight Configured
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isJp) "体重登録完了: ${savedWeight ?: 70f} kg" else "Athlete weight calibrated: ${savedWeight ?: 70f} kg",
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
                            if (isJp) "高精度ハードウェア歩数トラッキング完了" else "Hardware footstep tracking: Active"
                        } else {
                            if (isJp) "歩数トラッキング権限: 未承認 (制限あり)" else "Hardware step permissions: Missing"
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
                            if (isJp) "ヘルスコネクト: 連携中 (自動同期)" else "Health Connect: Connected"
                        } else {
                            if (isJp) "ヘルスコネクト: 未連携 (後で設定可)" else "Health Connect: Skipped for now"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
