package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.WalkingRepository
import com.example.ui.WalkingViewModel
import com.example.ui.WalkingViewModelFactory
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme

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
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isOnboardingCompleted) {
                        com.example.ui.screens.OnboardingScreen(
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
                            onThemeToggle = { viewModel.toggleTheme() },
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
