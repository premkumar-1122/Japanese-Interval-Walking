package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.WalkingRepository
import com.example.data.WalkingSession
import com.example.service.WalkingForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalkingViewModel(
    application: Application,
    private val repository: WalkingRepository
) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences(
        "japanese_interval_walking_prefs",
        Context.MODE_PRIVATE
    )

    val onboardingPrefs = com.example.data.OnboardingPrefs(application)

    val isOnboardingCompleted: StateFlow<Boolean> = onboardingPrefs.isOnboardingCompleted
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val onboardingWeight: StateFlow<Float?> = onboardingPrefs.userWeight
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val weeklyWalkGoal: StateFlow<Int> = onboardingPrefs.weeklyWalkGoal
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 5
        )

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            onboardingPrefs.setOnboardingCompleted(completed)
            if (completed) {
                com.example.data.OnboardingAnalytics.logEvent("onboarding_completed")
                if (!onboardingPrefs.hasSeededSession0.first()) {
                    val session0 = com.example.data.WalkingSession(
                        dateMillis = System.currentTimeMillis(),
                        durationSeconds = 0L,
                        steps = 0,
                        calories = 0.0,
                        avgCadence = 0.0,
                        avgPace = 0.0,
                        slowCyclesCount = 0,
                        fastCyclesCount = 0,
                        totalCycles = 0,
                        isSyncedToGoogleFit = false,
                        isSyncedToHealthConnect = false
                    )
                    repository.insertSession(session0)
                    onboardingPrefs.setSeededSession0(true)
                }
            } else {
                com.example.data.OnboardingAnalytics.logEvent("onboarding_started")
            }
        }
    }

    fun setOnboardingWeight(weight: Float) {
        viewModelScope.launch {
            onboardingPrefs.setUserWeight(weight)
            updateWeight(weight) // keeps sharedPrefs weight synced so existing service reads correct weight
            com.example.data.OnboardingAnalytics.logEvent("weight_saved", mapOf("weight" to weight))
        }
    }

    fun setWeeklyWalkGoal(goal: Int) {
        viewModelScope.launch {
            onboardingPrefs.setWeeklyWalkGoal(goal)
            com.example.data.OnboardingAnalytics.logEvent("weekly_goal_saved", mapOf("goal" to goal))
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            onboardingPrefs.resetOnboarding()
            com.example.data.OnboardingAnalytics.logEvent("onboarding_reset")
        }
    }

    fun replayAppTour() {
        viewModelScope.launch {
            // Simply mark incomplete to show tour again
            onboardingPrefs.setOnboardingCompleted(false)
            com.example.data.OnboardingAnalytics.logEvent("onboarding_replayed")
        }
    }

    fun logOnboardingEvent(event: String, params: Map<String, Any> = emptyMap()) {
        com.example.data.OnboardingAnalytics.logEvent(event, params)
    }

    // Reactive Flow of Historical Sessions logged in Room
    val historyState: StateFlow<List<WalkingSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current service tracking state flow
    val serviceState: StateFlow<WalkingForegroundService.ServiceState> = WalkingForegroundService.currentState

    // Application Preferences and Settings properties (Stored locally)
    private val _userWeight = MutableStateFlow(sharedPrefs.getFloat("pref_user_weight", 70f))
    val userWeight: StateFlow<Float> = _userWeight.asStateFlow()

    private val _isWeightUnitKg = MutableStateFlow(sharedPrefs.getBoolean("pref_is_weight_unit_kg", true))
    val isWeightUnitKg: StateFlow<Boolean> = _isWeightUnitKg.asStateFlow()

    private val _isVoiceEnabled = MutableStateFlow(sharedPrefs.getBoolean("pref_is_voice_enabled", true))
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val _isAudioEnabled = MutableStateFlow(sharedPrefs.getBoolean("pref_is_audio_enabled", true))
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("pref_is_dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isGoogleFitConnected = MutableStateFlow(sharedPrefs.getBoolean("pref_gf_connected", false))
    val isGoogleFitConnected: StateFlow<Boolean> = _isGoogleFitConnected.asStateFlow()

    private val _isHealthConnectConnected = MutableStateFlow(sharedPrefs.getBoolean("pref_hc_connected", false))
    val isHealthConnectConnected: StateFlow<Boolean> = _isHealthConnectConnected.asStateFlow()

    // Active custom intervals parameters set by user
    private val _customSlowMinutes = MutableStateFlow(sharedPrefs.getInt("pref_custom_slow", 3))
    val customSlowMinutes: StateFlow<Int> = _customSlowMinutes.asStateFlow()

    private val _customFastMinutes = MutableStateFlow(sharedPrefs.getInt("pref_custom_fast", 3))
    val customFastMinutes: StateFlow<Int> = _customFastMinutes.asStateFlow()

    private val _customCycles = MutableStateFlow(sharedPrefs.getInt("pref_custom_cycles", 5))
    val customCycles: StateFlow<Int> = _customCycles.asStateFlow()

    // Multi-language support properties
    private val _isJpLanguage = MutableStateFlow(sharedPrefs.getBoolean("pref_is_jp_language", false))
    val isJpLanguage: StateFlow<Boolean> = _isJpLanguage.asStateFlow()

    // Persistent storage for selected workout preset index (0 = Beginner, 1 = Standard, 2 = Advanced, 3 = Custom)
    private val _selectedPresetIndex = MutableStateFlow(sharedPrefs.getInt("pref_selected_preset_index", 1))
    val selectedPresetIndex: StateFlow<Int> = _selectedPresetIndex.asStateFlow()

    fun setSelectedPresetIndex(index: Int) {
        _selectedPresetIndex.value = index
        sharedPrefs.edit().putInt("pref_selected_preset_index", index).apply()
    }

    // Workout Reminder State configs
    private val _reminderEnabled = MutableStateFlow(sharedPrefs.getBoolean("pref_reminder_enabled", true))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(sharedPrefs.getInt("pref_reminder_hour", 8))
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(sharedPrefs.getInt("pref_reminder_minute", 30))
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    // Offline Syncing progress metrics
    private val _syncingProgress = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncingProgress: StateFlow<SyncStatus> = _syncingProgress.asStateFlow()

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val syncedCount: Int) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    val healthConnectManager = com.example.data.HealthConnectManager(application)
    val healthConnectPermissionManager = com.example.data.HealthConnectPermissionManager(application)

    private val _hasHealthConnectPermissions = MutableStateFlow(false)
    val hasHealthConnectPermissions: StateFlow<Boolean> = _hasHealthConnectPermissions.asStateFlow()

    private val _lastSyncMetadata = MutableStateFlow(healthConnectManager.getSyncMetadata())
    val lastSyncMetadata: StateFlow<com.example.data.SyncMetadata> = _lastSyncMetadata.asStateFlow()

    fun checkHealthConnectPermissions() {
        healthConnectManager.invalidateCachedStatus()
        viewModelScope.launch {
            _hasHealthConnectPermissions.value = healthConnectPermissionManager.hasAllPermissions()
        }
    }

    fun refreshSyncMetadata() {
        _lastSyncMetadata.value = healthConnectManager.getSyncMetadata()
    }

    init {
        // Feed settings variables immediately to background Service companion defaults
        syncServiceCompanionValues()
        checkHealthConnectPermissions()
        refreshSyncMetadata()
        viewModelScope.launch {
            onboardingPrefs.userWeight.collect { weightVal ->
                if (weightVal != null) {
                    _userWeight.value = weightVal
                }
            }
        }
    }

    private fun syncServiceCompanionValues() {
        WalkingForegroundService.slowDurationMinutes = _customSlowMinutes.value
        WalkingForegroundService.fastDurationMinutes = _customFastMinutes.value
        WalkingForegroundService.selectedCycles = _customCycles.value
        WalkingForegroundService.isVoiceEnabled = _isVoiceEnabled.value
        WalkingForegroundService.isAudioEnabled = _isAudioEnabled.value
        WalkingForegroundService.userWeightKg = _userWeight.value
        WalkingForegroundService.isJpLanguage = _isJpLanguage.value
    }

    // Command APIs triggered by UI layout
    fun startWorkout(slowMin: Int, fastMin: Int, cycles: Int, preset: String) {
        val app = getApplication<Application>()
        
        WalkingForegroundService.slowDurationMinutes = slowMin
        WalkingForegroundService.fastDurationMinutes = fastMin
        WalkingForegroundService.selectedCycles = cycles
        WalkingForegroundService.presetName = preset
        WalkingForegroundService.userWeightKg = _userWeight.value
        WalkingForegroundService.isVoiceEnabled = _isVoiceEnabled.value
        WalkingForegroundService.isAudioEnabled = _isAudioEnabled.value
        WalkingForegroundService.isJpLanguage = _isJpLanguage.value

        val intent = Intent(app, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_START
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(app, intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun pauseWorkout() {
        if (serviceState.value is WalkingForegroundService.ServiceState.Idle) {
            return
        }
        val app = getApplication<Application>()
        val intent = Intent(app, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_PAUSE
        }
        try {
            app.startService(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun resumeWorkout() {
        if (serviceState.value is WalkingForegroundService.ServiceState.Idle) {
            return
        }
        val app = getApplication<Application>()
        val intent = Intent(app, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_START
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(app, intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun skipIntervalPhase() {
        if (serviceState.value is WalkingForegroundService.ServiceState.Idle) {
            return
        }
        val app = getApplication<Application>()
        val intent = Intent(app, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_SKIP
        }
        try {
            app.startService(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun stopWorkout() {
        if (serviceState.value is WalkingForegroundService.ServiceState.Idle) {
            return
        }
        val app = getApplication<Application>()
        val intent = Intent(app, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_STOP
        }
        try {
            app.startService(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // Preference adjust controllers
    fun toggleTheme() {
        val nextMode = !_isDarkTheme.value
        _isDarkTheme.value = nextMode
        sharedPrefs.edit().putBoolean("pref_is_dark_theme", nextMode).apply()
    }

    fun toggleWeightUnit() {
        val next = !_isWeightUnitKg.value
        _isWeightUnitKg.value = next
        sharedPrefs.edit().putBoolean("pref_is_weight_unit_kg", next).apply()
    }

    fun toggleVoice() {
        val next = !_isVoiceEnabled.value
        _isVoiceEnabled.value = next
        sharedPrefs.edit().putBoolean("pref_is_voice_enabled", next).apply()
        syncServiceCompanionValues()
    }

    fun toggleAudio() {
        val next = !_isAudioEnabled.value
        _isAudioEnabled.value = next
        sharedPrefs.edit().putBoolean("pref_is_audio_enabled", next).apply()
        syncServiceCompanionValues()
    }

    fun toggleLanguage() {
        val next = !_isJpLanguage.value
        _isJpLanguage.value = next
        sharedPrefs.edit().putBoolean("pref_is_jp_language", next).apply()
        syncServiceCompanionValues()
    }

    fun toggleGoogleFit() {
        val next = !_isGoogleFitConnected.value
        _isGoogleFitConnected.value = next
        sharedPrefs.edit().putBoolean("pref_gf_connected", next).apply()
    }

    fun toggleHealthConnect() {
        val next = !_isHealthConnectConnected.value
        _isHealthConnectConnected.value = next
        sharedPrefs.edit().putBoolean("pref_hc_connected", next).apply()
    }

    fun updateWeight(weight: Float) {
        val finalWt = weight.coerceIn(20f, 300f)
        _userWeight.value = finalWt
        sharedPrefs.edit().putFloat("pref_user_weight", finalWt).apply()
        syncServiceCompanionValues()
    }

    fun updateCustomIntervals(slow: Int, fast: Int, cycles: Int) {
        _customSlowMinutes.value = slow.coerceIn(1, 15)
        _customFastMinutes.value = fast.coerceIn(1, 15)
        _customCycles.value = cycles.coerceIn(1, 20)
        
        sharedPrefs.edit()
            .putInt("pref_custom_slow", _customSlowMinutes.value)
            .putInt("pref_custom_fast", _customFastMinutes.value)
            .putInt("pref_custom_cycles", _customCycles.value)
            .apply()
            
        syncServiceCompanionValues()
    }

    fun updateReminderHourAndMin(hour: Int, min: Int) {
        _reminderHour.value = hour.coerceIn(0, 23)
        _reminderMinute.value = min.coerceIn(0, 59)
        sharedPrefs.edit()
            .putInt("pref_reminder_hour", _reminderHour.value)
            .putInt("pref_reminder_minute", _reminderMinute.value)
            .apply()
    }

    fun toggleReminder() {
        val next = !_reminderEnabled.value
        _reminderEnabled.value = next
        sharedPrefs.edit().putBoolean("pref_reminder_enabled", next).apply()
    }

    // Real Android Health Connect Syncing Flow
    fun syncUnsyncedSessions(isOnline: Boolean = true) {
        viewModelScope.launch {
            _syncingProgress.value = SyncStatus.Syncing
            
            val manager = healthConnectManager
            if (!manager.isSdkAvailable()) {
                _syncingProgress.value = SyncStatus.Error(
                    if (_isJpLanguage.value) "ヘルスコネクトが利用できません。" else "Health Connect is not available on this device."
                )
                delay(2500)
                _syncingProgress.value = SyncStatus.Idle
                return@launch
            }
            
            if (!manager.hasAllPermissions()) {
                _syncingProgress.value = SyncStatus.Error(
                    if (_isJpLanguage.value) "ヘルスコネクトの権限が不足しています。" else "Missing Health Connect permissions."
                )
                delay(2500)
                _syncingProgress.value = SyncStatus.Idle
                return@launch
            }

            val currentSessions = historyState.value
            val unsynced = currentSessions.filter { !it.isSyncedToHealthConnect }

            if (unsynced.isEmpty()) {
                _syncingProgress.value = SyncStatus.Success(0)
                delay(2500)
                _syncingProgress.value = SyncStatus.Idle
                return@launch
            }

            var syncedCount = 0
            for (session in unsynced) {
                // Ensure to skip zero / phantom / test records
                if (session.steps <= 0 || session.calories <= 0.0 || session.durationSeconds < 10) {
                    repository.updateSyncStatus(session.id, googleFit = session.isSyncedToGoogleFit, healthConnect = true)
                    continue
                }

                val success = manager.writeSession(session)
                if (success) {
                    repository.updateSyncStatus(session.id, googleFit = session.isSyncedToGoogleFit, healthConnect = true)
                    syncedCount++
                }
            }

            _syncingProgress.value = SyncStatus.Success(syncedCount)
            refreshSyncMetadata()
            delay(3000)
            _syncingProgress.value = SyncStatus.Idle
        }
    }

    fun resetSyncStatusState() {
        _syncingProgress.value = SyncStatus.Idle
    }

    fun deleteLoggedSession(session: WalkingSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun clearAllHistoryLogs() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Dynamic Social Share Text Generator
    fun getShareContent(session: WalkingSession): Pair<String, String> {
        val minuteFmt = session.durationSeconds / 60
        val secFmt = session.durationSeconds % 60
        val durationStr = String.format("%02d:%02d", minuteFmt, secFmt)

        val title = if (_isJpLanguage.value) {
            "運動完了！インターバル速歩"
        } else {
            "Workout Accomplished! - Japanese Interval Walk"
        }

        val jpBody = """
            [インターバル速歩 トレーニング記録]
            
            3分ずつの「ゆっくり歩き」と「早歩き」を交互に繰り返す科学的フィットネスを完了しました！
            
            [運動統計]
            - 総歩数: ${session.steps} 歩
            - 消費カロリー: ${String.format("%.1f", session.calories)} kcal
            - 運動時間: $durationStr 分
            - 平均ケイデンス: ${String.format("%.0f", session.avgCadence)} 歩/分
            - 平均ペース: ${String.format("%.1f", session.avgPace)} 分/km
            - インターバル回数: ${session.totalCycles} サイクル (${session.slowCyclesCount}x低速, ${session.fastCyclesCount}x高速)
            
            [トラッカー] #JapaneseIntervalWalking
        """.trimIndent()

        val enBody = """
            [Japanese Interval Walking Record]
            
            Scientific alternating 3-min fast / 3-min slow walking workout has been successfully registered!
            
            [Workout Stats]
            - Steps Taken: ${session.steps} steps
            - Calories Burned: ${String.format("%.1f", session.calories)} kcal
            - Walking Duration: $durationStr min
            - Avg Cadence: ${String.format("%.0f", session.avgCadence)} step/min
            - Average Pace: ${String.format("%.1f", session.avgPace)} min/km
            - Total Intervals: ${session.totalCycles} cycles (${session.slowCyclesCount}x recovery, ${session.fastCyclesCount}x peak)
            
            [Tracker App] #JapaneseIntervalWalking
        """.trimIndent()

        return title to (if (_isJpLanguage.value) jpBody else enBody)
    }
}

// ViewModel Factory to Inject application & repository dependencies
class WalkingViewModelFactory(
    private val application: Application,
    private val repository: WalkingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalkingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalkingViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
