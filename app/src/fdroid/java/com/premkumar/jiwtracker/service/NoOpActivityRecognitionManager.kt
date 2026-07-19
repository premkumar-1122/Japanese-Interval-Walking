package com.premkumar.jiwtracker.service

import android.content.Intent

class NoOpActivityRecognitionManager : IActivityRecognitionManager {

    override val isWalkingOrRunning: Boolean = true
    override val detectedActivityType: Int = 7 // DetectedActivity.WALKING = 7

    override fun register(context: WalkingForegroundService) {
    }

    override fun unregister() {
    }

    override fun handleActivityRecognition(intent: Intent?) {
    }
}
