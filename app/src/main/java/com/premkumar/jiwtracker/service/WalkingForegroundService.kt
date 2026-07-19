package com.premkumar.jiwtracker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.premkumar.jiwtracker.MainActivity
import com.premkumar.jiwtracker.R
import com.premkumar.jiwtracker.data.AppDatabase
import com.premkumar.jiwtracker.data.WalkingRepository
import com.premkumar.jiwtracker.data.WalkingSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

data class CompletedSessionData(
    val steps: Int,
    val calories: Double,
    val durationSeconds: Long,
    val slowCyclesCount: Int,
    val fastCyclesCount: Int,
    val totalCycles: Int,
    val avgCadence: Double,
    val avgPace: Double,
    val dateMillis: Long
)

class WalkingForegroundService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "walking_session_channel"
        const val NOTIFICATION_ID = 4136

        const val ACTION_START = "com.premkumar.jiwtracker.action.START"
        const val ACTION_PAUSE = "com.premkumar.jiwtracker.action.PAUSE"
        const val ACTION_SKIP = "com.premkumar.jiwtracker.action.SKIP"
        const val ACTION_STOP = "com.premkumar.jiwtracker.action.STOP"
        const val ACTION_ACTIVITY_RECOGNITION = "com.premkumar.jiwtracker.action.ACTIVITY_RECOGNITION"

        // Active State of Current Session, reachable directly by UI ViewModels
        private val _currentState = MutableStateFlow<ServiceState>(ServiceState.Idle)
        val currentState: StateFlow<ServiceState> = _currentState

        private val _completedSession = MutableStateFlow<CompletedSessionData?>(null)
        val completedSession: StateFlow<CompletedSessionData?> = _completedSession

        fun clearCompletedSession() {
            _completedSession.value = null
        }

        // Toggle properties configured by Settings (defaults)
        var slowDurationMinutes: Int = 3
        var fastDurationMinutes: Int = 3
        var selectedCycles: Int = 5
        var isVoiceEnabled: Boolean = true
        var isAudioEnabled: Boolean = true
        var userWeightKg: Float = 70f
        var presetName: String = "Standard Session"
        var isJpLanguage: Boolean = false // Track app language
    }

    sealed class ServiceState {
        object Idle : ServiceState()
        data class Active(
            val isRunning: Boolean,
            val currentPhase: Phase, // SLOW vs FAST
            val timeLeftInPhaseSeconds: Int,
            val phaseDurationTotalSeconds: Int,
            val elapsedTotalSeconds: Int,
            val currentCycle: Int,
            val totalCycles: Int,
            val steps: Int,
            val cadence: Int, // Steps per Minute (SPM)
            val pace: Double, // Min/Km
            val calories: Double, // kcal
            val slowCyclesCompleted: Int,
            val fastCyclesCompleted: Int
        ) : ServiceState()
    }

    enum class Phase {
        SLOW, FAST
    }

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var activeState = ServiceState.Active(
        isRunning = false,
        currentPhase = Phase.SLOW,
        timeLeftInPhaseSeconds = slowDurationMinutes * 60,
        phaseDurationTotalSeconds = slowDurationMinutes * 60,
        elapsedTotalSeconds = 0,
        currentCycle = 1,
        totalCycles = selectedCycles,
        steps = 0,
        cadence = 0,
        pace = 0.0,
        calories = 0.0,
        slowCyclesCompleted = 0,
        fastCyclesCompleted = 0
    )

    private var stepSensor: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    
    private var tickerJob: Job? = null
    private var stopSelfJob: Job? = null
    private var initialStepsInHardware = -1
    private var lastHardwareStepCount = -1
    private var lastCalculationStepCount = 0
    private var lastMinuteTime = System.currentTimeMillis()
    private var isListenerRegistered = false
    private val stepTimeTracker = java.util.Collections.synchronizedList(mutableListOf<Long>())

    // Activity Recognition Integration
    private val activityRecognitionManager: IActivityRecognitionManager = ActivityRecognitionProvider.create()
    private var activeDurationSeconds = 0

    fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupWakeLock()
        
        // Initialize TTS
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Throwable) {
            e.printStackTrace()
            tts = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> pauseSession()
            ACTION_SKIP -> skipPhase()
            ACTION_STOP -> stopSession(saveToDb = true)
            ACTION_ACTIVITY_RECOGNITION -> activityRecognitionManager.handleActivityRecognition(intent)
        }
        return START_STICKY
    }

    private fun registerSensor() {
        if (isListenerRegistered) return
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepSensor != null) {
                sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
                isListenerRegistered = true
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun unregisterSensor() {
        if (!isListenerRegistered) return
        try {
            sensorManager?.unregisterListener(this)
            isListenerRegistered = false
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun setupWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalWalking::TrackingWakelock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun startSession() {
        stopSelfJob?.cancel()
        stopSelfJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!hasActivityRecognitionPermission()) {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(activeState))
                } catch (fallbackEx: Throwable) {
                    fallbackEx.printStackTrace()
                }
                stopSelf()
                return
            }
        }

        val wasIdle = _currentState.value is ServiceState.Idle
        if (wasIdle) {
            // New Session begins
            activeState = ServiceState.Active(
                isRunning = true,
                currentPhase = Phase.SLOW,
                timeLeftInPhaseSeconds = slowDurationMinutes * 60,
                phaseDurationTotalSeconds = slowDurationMinutes * 60,
                elapsedTotalSeconds = 0,
                currentCycle = 1,
                totalCycles = selectedCycles,
                steps = 0,
                cadence = 0,
                pace = 0.0,
                calories = 0.0,
                slowCyclesCompleted = 0,
                fastCyclesCompleted = 0
            )
            initialStepsInHardware = -1
            lastCalculationStepCount = 0
            activeDurationSeconds = 0
            synchronized(stepTimeTracker) {
                stepTimeTracker.clear()
            }
            speakCue("Starting walking session. Pace yourselves, three minutes slow walk begins.")
        } else {
            // Resume Session
            activeState = activeState.copy(isRunning = true)
            speakCue("Resuming workout.")
        }
        
        lastHardwareStepCount = -1
        registerSensor()
        activityRecognitionManager.register(this)
        _currentState.value = activeState
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(activeState),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(activeState))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, buildNotification(activeState))
            } catch (fallbackEx: Throwable) {
                fallbackEx.printStackTrace()
            }
            stopSelf()
            return
        }

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(120 * 60 * 1000L /*2 hours limit*/)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        startTicker()
    }

    private fun pauseSession() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        activeState = activeState.copy(isRunning = false, cadence = 0, pace = 0.0)
        _currentState.value = activeState
        stopTicker()
        unregisterSensor()
        activityRecognitionManager.unregister()
        updateNotification(activeState)
        speakCue("Workout paused.")
    }

    private fun skipPhase() {
        transitionInterval()
    }

    private fun stopSession(saveToDb: Boolean) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        stopTicker()
        unregisterSensor()
        activityRecognitionManager.unregister()
        
        val record = activeState
        _currentState.value = ServiceState.Idle

        if (saveToDb && record.steps > 0) {
            val totalDistanceKm = (record.steps.toDouble() * 0.72) / 1000.0
            val durationMinutes = record.elapsedTotalSeconds.toDouble() / 60.0
            val calculatedAvgPace = if (totalDistanceKm > 0.0) (durationMinutes / totalDistanceKm) else 0.0
            val avgCadInstance = if (record.elapsedTotalSeconds > 0) (record.steps.toDouble() / (record.elapsedTotalSeconds.toDouble() / 60.0)) else 0.0

            val completedData = CompletedSessionData(
                steps = record.steps,
                calories = record.calories,
                durationSeconds = record.elapsedTotalSeconds.toLong(),
                slowCyclesCount = record.slowCyclesCompleted,
                fastCyclesCount = record.fastCyclesCompleted,
                totalCycles = record.currentCycle,
                avgCadence = avgCadInstance,
                avgPace = calculatedAvgPace,
                dateMillis = System.currentTimeMillis()
            )
            _completedSession.value = completedData

            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val repo = WalkingRepository(db.walkingSessionDao())
                val session = WalkingSession(
                    dateMillis = completedData.dateMillis,
                    durationSeconds = completedData.durationSeconds,
                    steps = completedData.steps,
                    calories = completedData.calories,
                    avgCadence = completedData.avgCadence,
                    avgPace = completedData.avgPace,
                    slowCyclesCount = completedData.slowCyclesCount,
                    fastCyclesCount = completedData.fastCyclesCount,
                    totalCycles = completedData.totalCycles,
                    isSyncedToGoogleFit = false,
                    isSyncedToHealthConnect = false
                )
                repo.insertSession(session)
                com.premkumar.jiwtracker.service.HealthConnectSyncWorker.enqueueSync(applicationContext)
            }
        }

        speakCue("Session completed")
        stopSelfJob?.cancel()
        stopSelfJob = serviceScope.launch {
            delay(5000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                tickOneSecond()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun tickOneSecond() {
        val nextElapsed = activeState.elapsedTotalSeconds + 1
        var nextTimeLeft = activeState.timeLeftInPhaseSeconds - 1
        var nextCycle = activeState.currentCycle
        var nextPhase = activeState.currentPhase

        var slowCompleted = activeState.slowCyclesCompleted
        var fastCompleted = activeState.fastCyclesCompleted

        // No simulated steps! Purely use actual steps tracked via sensor changes.
        val nextSteps = activeState.steps

        // Dynamic actual cadence calculated from timestamps of footsteps in the last 15 seconds:
        val now = System.currentTimeMillis()
        synchronized(stepTimeTracker) {
            stepTimeTracker.removeAll { now - it > 15000L }
        }
        val nextCadence = (stepTimeTracker.size * 4).coerceAtMost(250)

        // Pace (minutes per km) calculated dynamically from cadence:
        val strideLength = if (activeState.currentPhase == Phase.FAST) 0.75 else 0.7
        val nextPace = if (nextCadence > 10) {
            val speedMetersPerMin = strideLength * nextCadence
            if (speedMetersPerMin > 0.0) {
                val p = 1000.0 / speedMetersPerMin
                if (p > 30.0) 30.0 else p
            } else {
                0.0
            }
        } else {
            0.0
        }

        // Calorie calculation using MET values based on speed ranges
        val stepsChanged = nextSteps > lastCalculationStepCount
        val isCurrentlyActive = stepsChanged || nextCadence > 0

        val nextCalories = if (isCurrentlyActive) {
            val speedKmh = if (nextPace > 0.0) 60.0 / nextPace else 0.0
            val metValue = when {
                speedKmh <= 0.0 -> if (activeState.currentPhase == Phase.FAST) 5.2 else 3.2
                speedKmh < 3.0 -> 2.0
                speedKmh < 4.0 -> 2.8
                speedKmh < 5.0 -> 3.5
                speedKmh < 6.0 -> 4.3
                speedKmh < 7.0 -> 5.0
                speedKmh < 8.0 -> 7.0
                speedKmh < 10.0 -> 9.0
                else -> 11.0
            }
            activeDurationSeconds++
            val calorieBurnedThisSecond = (metValue * userWeightKg) / 3600.0
            activeState.calories + calorieBurnedThisSecond
        } else {
            activeState.calories
        }

        lastCalculationStepCount = nextSteps

        if (nextTimeLeft <= 0) {
            // Current Interval Completed!
            if (activeState.currentPhase == Phase.SLOW) {
                slowCompleted++
                nextPhase = Phase.FAST
                nextTimeLeft = fastDurationMinutes * 60
                speakCue("Slow interval complete. Speed up! Three minutes fast walk starts now.")
            } else {
                fastCompleted++
                nextCycle++
                if (nextCycle > activeState.totalCycles) {
                    // Entire Workout Session Finished!
                    activeState = activeState.copy(
                        elapsedTotalSeconds = nextElapsed,
                        timeLeftInPhaseSeconds = 0,
                        steps = nextSteps,
                        calories = nextCalories,
                        cadence = 0,
                        pace = 0.0,
                        slowCyclesCompleted = slowCompleted,
                        fastCyclesCompleted = fastCompleted
                    )
                    stopSession(saveToDb = true)
                    return
                } else {
                    nextPhase = Phase.SLOW
                    nextTimeLeft = slowDurationMinutes * 60
                    speakCue("Fast interval complete. Slow down! Walk comfortably for recovery.")
                }
            }
        }

        activeState = activeState.copy(
            timeLeftInPhaseSeconds = nextTimeLeft,
            phaseDurationTotalSeconds = if (nextPhase == Phase.SLOW) slowDurationMinutes * 60 else fastDurationMinutes * 60,
            currentPhase = nextPhase,
            elapsedTotalSeconds = nextElapsed,
            currentCycle = nextCycle,
            steps = nextSteps,
            calories = nextCalories,
            cadence = nextCadence,
            pace = nextPace,
            slowCyclesCompleted = slowCompleted,
            fastCyclesCompleted = fastCompleted
        )

        _currentState.value = activeState
        updateNotification(activeState)
    }

    private fun transitionInterval() {
        var nextCycle = activeState.currentCycle
        var nextPhase = activeState.currentPhase
        var nextTimeLeft = activeState.timeLeftInPhaseSeconds
        var slowCompleted = activeState.slowCyclesCompleted
        var fastCompleted = activeState.fastCyclesCompleted

        val remainingInPhase = activeState.timeLeftInPhaseSeconds
        val nextElapsed = activeState.elapsedTotalSeconds + remainingInPhase

        // Do NOT simulate any steps or calories for skipped time
        val nextSteps = activeState.steps
        val nextCalories = activeState.calories

        if (activeState.currentPhase == Phase.SLOW) {
            slowCompleted++
            nextPhase = Phase.FAST
            nextTimeLeft = fastDurationMinutes * 60
            speakCue("Slow interval complete. Speed up! Three minutes fast walk starts now.")
        } else {
            fastCompleted++
            nextCycle++
            if (nextCycle > activeState.totalCycles) {
                activeState = activeState.copy(
                    elapsedTotalSeconds = nextElapsed,
                    timeLeftInPhaseSeconds = 0,
                    steps = nextSteps,
                    calories = nextCalories,
                    cadence = 0,
                    pace = 0.0,
                    slowCyclesCompleted = slowCompleted,
                    fastCyclesCompleted = fastCompleted
                )
                stopSession(saveToDb = true)
                return
            }
            nextPhase = Phase.SLOW
            nextTimeLeft = slowDurationMinutes * 60
            speakCue("Fast interval complete. Slow down! Walk comfortably for recovery.")
        }

        activeState = activeState.copy(
            timeLeftInPhaseSeconds = nextTimeLeft,
            phaseDurationTotalSeconds = if (nextPhase == Phase.SLOW) slowDurationMinutes * 60 else fastDurationMinutes * 60,
            currentPhase = nextPhase,
            currentCycle = nextCycle,
            elapsedTotalSeconds = nextElapsed,
            steps = nextSteps,
            calories = nextCalories,
            slowCyclesCompleted = slowCompleted,
            fastCyclesCompleted = fastCompleted
        )
        _currentState.value = activeState
        updateNotification(activeState)
    }

    private fun speakCue(text: String) {
        if (!isVoiceEnabled || !isTtsInitialized) return
        val textToSpeak = if (isJpLanguage) {
            translateToJapanese(text)
        } else {
            text
        }
        try {
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "WalkingServiceCue")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun translateToJapanese(englishText: String): String {
        return when {
            englishText.contains("Starting") -> "インターバルトレーニングを開始します。最初はマイペースでゆっくり3分間歩きましょう。"
            englishText.contains("Resuming") -> "トレーニングを再開します。"
            englishText.contains("paused") -> "一時停止しました。"
            englishText.contains("Next interval") -> "次の区間へ移行します。"
            englishText.contains("Slow interval complete") -> "ゆっくり歩き終了です。ギヤを上げて、3分間の早歩きを始めましょう！"
            englishText.contains("Fast interval complete") -> "早歩きが終了しました。息を整えながら、ゆっくりマイペースで歩きましょう。"
            englishText.contains("Session completed") -> "セッションが自動完了しました。お疲れ様でした。"
            englishText.contains("Workout completed") -> "お疲れ様でした！インターバルトレーニングが完了しました。素晴らしい運動量です。"
            else -> englishText
        }
    }

    // Interactive Persistent Notification Builder
    private fun buildNotification(state: ServiceState.Active): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Dynamic play/pause notification controls
        val playPauseActionIntent = Intent(this, WalkingForegroundService::class.java).apply {
            action = if (state.isRunning) ACTION_PAUSE else ACTION_START
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipActionIntent = Intent(this, WalkingForegroundService::class.java).apply {
            action = ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getService(
            this, 2, skipActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val durationLabel = formatTimerTime(state.timeLeftInPhaseSeconds)
        val phaseLabelText = if (state.currentPhase == Phase.FAST) {
            if (isJpLanguage) "早歩き区間 (FAST)" else "FAST BURN PHASE"
        } else {
            if (isJpLanguage) "ゆっくり区間 (SLOW)" else "SLOW RECOVERY PHASE"
        }

        val progressMax = state.phaseDurationTotalSeconds
        val progressCurrent = progressMax - state.timeLeftInPhaseSeconds

        val notificationTitle = if (isJpLanguage) {
             "歩数: ${state.steps}歩 | $phaseLabelText ($durationLabel)"
        } else {
             "Steps: ${state.steps} | $phaseLabelText ($durationLabel)"
        }

        val cycleText = if (isJpLanguage) {
             "サイクル: ${state.currentCycle}/${state.totalCycles} | ${String.format(Locale.getDefault(), "%.1f", state.calories)} kcal"
        } else {
             "Cycle ${state.currentCycle} of ${state.totalCycles} | Burn: ${String.format(Locale.getDefault(), "%.1f", state.calories)} kcal"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) // Use general standard icon for compilation stability
            .setContentTitle(notificationTitle)
            .setContentText(cycleText)
            .setSubText(presetName)
            .setProgress(progressMax, progressCurrent, false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Bold design styled action buttons
        if (state.isRunning) {
            builder.addAction(android.R.drawable.ic_media_pause, if (isJpLanguage) "一時停止" else "PAUSE", playPausePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, if (isJpLanguage) "再開" else "RESUME", playPausePendingIntent)
        }

        builder.addAction(android.R.drawable.ic_media_next, if (isJpLanguage) "スキップ" else "SKIP", skipPendingIntent)

        return builder.build()
    }

    private fun updateNotification(state: ServiceState.Active) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Walking Session Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing persistent workout status and statistics."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun formatTimerTime(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    // Hardware Step Counter Sensor Logic
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsInHardware = event.values[0].toInt()
            if (lastHardwareStepCount < 0) {
                lastHardwareStepCount = totalStepsInHardware
            }
            val delta = totalStepsInHardware - lastHardwareStepCount
            lastHardwareStepCount = totalStepsInHardware

            if (activeState.isRunning && delta > 0) {
                val now = System.currentTimeMillis()
                synchronized(stepTimeTracker) {
                    for (i in 0 until delta) {
                        stepTimeTracker.add(now)
                    }
                }
                val nextSteps = activeState.steps + delta

                activeState = activeState.copy(
                    steps = nextSteps
                )
                _currentState.value = activeState
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            try {
                tts?.language = if (isJpLanguage) Locale.JAPANESE else Locale.ENGLISH
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        stopTicker()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        unregisterSensor()
        
        // Destruct TTS
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Unused binding. ViewModel watches companion MutableStateFlow of state directly
    }
}
