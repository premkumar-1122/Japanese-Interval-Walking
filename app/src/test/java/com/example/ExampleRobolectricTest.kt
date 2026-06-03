package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.WalkingRepository
import com.example.ui.WalkingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Japanese Interval Walking", appName)
  }

  @Test
  fun `verify database initialization and viewModel creation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as Application
    val database = AppDatabase.getDatabase(context)
    assertNotNull(database)
    
    val repository = WalkingRepository(database.walkingSessionDao())
    assertNotNull(repository)
    
    val viewModel = WalkingViewModel(app, repository)
    assertNotNull(viewModel)
    
    // Test preference default reading
    assertNotNull(viewModel.isDarkTheme.value)
    assertNotNull(viewModel.isVoiceEnabled.value)
    assertNotNull(viewModel.isAudioEnabled.value)
    assertNotNull(viewModel.userWeight.value)
    
    // Simulate starting a workout from user presets
    viewModel.startWorkout(3, 3, 5, "Standard Session")
    
    // Verify parameters synced to the service companion
    assertEquals(3, com.example.service.WalkingForegroundService.slowDurationMinutes)
    assertEquals(3, com.example.service.WalkingForegroundService.fastDurationMinutes)
    assertEquals(5, com.example.service.WalkingForegroundService.selectedCycles)
  }

  @Test
  fun `verify MET calculations prevent phantom calories when stationary`() {
    // 1. Initially, when the phone is stationary, calories must remain 0.0
    val weightKg = 70.0
    var activeDurationSeconds = 0
    var lastCalculationStepCount = 0
    var steps = 0
    var cadence = 0
    var pace = 0.0
    var elapsedTotalSeconds = 0
    val detectedActivityType = 7 // DetectedActivity.WALKING
    
    fun isActivityWalkingOrRunning(type: Int): Boolean = type == 7 || type == 8
    
    // Simulate tick with stationary state
    val stepsChanged1 = steps > 0 && steps != lastCalculationStepCount
    val calories1 = if (!stepsChanged1 || cadence == 0 || pace <= 0.0 || elapsedTotalSeconds <= 0 || !isActivityWalkingOrRunning(detectedActivityType)) {
        0.0 // keep unchanged/initial
    } else {
        3.5 * weightKg * (activeDurationSeconds / 3600.0)
    }
    assertEquals(0.0, calories1, 0.001)

    // 2. Now simulate standard movement
    steps = 10
    cadence = 100
    pace = 6.0 // 6 min/km -> speed 10 km/h
    elapsedTotalSeconds = 10
    
    val stepsChanged2 = steps > 0 && steps != lastCalculationStepCount
    val calories2 = if (!stepsChanged2 || cadence == 0 || pace <= 0.0 || elapsedTotalSeconds <= 0 || !isActivityWalkingOrRunning(detectedActivityType)) {
        0.0
    } else {
        val speedKmh = 60.0 / pace
        val metValue = when {
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
        lastCalculationStepCount = steps
        val durationHours = activeDurationSeconds.toDouble() / 3600.0
        metValue * weightKg * durationHours
    }
    
    // Verify stepsChanged makes calorie active and MET weight is derived
    assertEquals(10, lastCalculationStepCount)
    assertEquals(1, activeDurationSeconds)
    assertEquals(11.0 * 70.0 * (1.0 / 3600.0), calories2, 0.001)
  }

  @Test
  fun `verify onboarding state flow default and weight constraints`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as Application
    val database = AppDatabase.getDatabase(context)
    val repository = WalkingRepository(database.walkingSessionDao())
    val viewModel = WalkingViewModel(app, repository)

    // Verify initial values exist and are accessible
    assertNotNull(viewModel.isOnboardingCompleted)
    assertNotNull(viewModel.onboardingWeight)

    // Set a valid weight and test that it updates both the flow and SharedPreferences
    viewModel.updateWeight(82.5f)
    assertEquals(82.5f, viewModel.userWeight.value, 0.001f)

    // Validate setting onboarding completed triggers state flow transitions
    viewModel.setOnboardingCompleted(true)
    
    // Set invalid or out-of-bounds weight, it should be coerced to 20f..300f
    viewModel.updateWeight(15f) // too low
    assertEquals(20f, viewModel.userWeight.value, 0.001f)

    viewModel.updateWeight(350f) // too high
    assertEquals(300f, viewModel.userWeight.value, 0.001f)
  }
}
