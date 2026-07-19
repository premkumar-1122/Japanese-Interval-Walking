/*
 * Copyright (C) 2026 Prem Kumar Gara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.premkumar.jiwtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premkumar.jiwtracker.data.AppDatabase
import com.premkumar.jiwtracker.data.WalkingRepository
import com.premkumar.jiwtracker.ui.WalkingViewModel
import com.premkumar.jiwtracker.ui.WalkingViewModelFactory
import com.premkumar.jiwtracker.ui.screens.DashboardScreen
import com.premkumar.jiwtracker.ui.theme.MyApplicationTheme

@androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core Room database and Repository instantiation
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WalkingRepository(database.walkingSessionDao())

        // ViewModel Factory construction
        val viewModel: WalkingViewModel = ViewModelProvider(
            this,
            WalkingViewModelFactory(application, repository)
        )[WalkingViewModel::class.java]

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkTheme = when(themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowInsetsController =
                        WindowInsetsControllerCompat(window, window.decorView)
                    windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                    windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme

                    val windowSizeClass = calculateWindowSizeClass(this)
                    if (!isOnboardingCompleted) {
                        com.premkumar.jiwtracker.ui.screens.OnboardingScreen(
                            viewModel = viewModel,
                            onOnboardingFinished = {
                                viewModel.setOnboardingCompleted(true)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            windowSizeClass = windowSizeClass,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// Auxiliary greeting composable target to preserve screenshot test compile integrity
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
