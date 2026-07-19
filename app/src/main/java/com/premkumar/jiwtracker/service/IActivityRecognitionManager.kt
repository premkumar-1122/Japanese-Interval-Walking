package com.premkumar.jiwtracker.service

import android.content.Intent

interface IActivityRecognitionManager {
    val isWalkingOrRunning: Boolean
    val detectedActivityType: Int

    fun register(context: WalkingForegroundService)
    fun unregister()
    fun handleActivityRecognition(intent: Intent?)
}
