package com.premkumar.jiwtracker.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class GmsActivityRecognitionManager : IActivityRecognitionManager {

    override var isWalkingOrRunning: Boolean = true
    override var detectedActivityType: Int = DetectedActivity.WALKING
    private var serviceRef: WalkingForegroundService? = null
    private var isRegistered = false

    override fun register(context: WalkingForegroundService) {
        if (isRegistered) return
        if (!context.hasActivityRecognitionPermission()) return
        serviceRef = context
        try {
            val client = ActivityRecognition.getClient(context)
            client.requestActivityUpdates(1000L, getPendingIntent(context))
                .addOnSuccessListener {
                    isRegistered = true
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun unregister() {
        val context = serviceRef ?: return
        if (!isRegistered) return
        try {
            val client = ActivityRecognition.getClient(context)
            client.removeActivityUpdates(getPendingIntent(context))
                .addOnCompleteListener {
                    isRegistered = false
                }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun handleActivityRecognition(intent: Intent?) {
        if (intent != null && ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            if (result != null) {
                val mostProbableActivity = result.mostProbableActivity
                if (mostProbableActivity != null) {
                    detectedActivityType = mostProbableActivity.type
                }

                val probableActs = result.probableActivities
                val walkingOrRunningProb = probableActs?.firstOrNull {
                    it.type == DetectedActivity.WALKING || it.type == DetectedActivity.RUNNING
                }
                val stillProb = probableActs?.firstOrNull { it.type == DetectedActivity.STILL }?.confidence ?: 0

                isWalkingOrRunning = if (mostProbableActivity != null &&
                    (mostProbableActivity.type == DetectedActivity.WALKING ||
                     mostProbableActivity.type == DetectedActivity.RUNNING)
                ) {
                    true
                } else if (walkingOrRunningProb != null && walkingOrRunningProb.confidence > 15) {
                    true
                } else {
                    !(stillProb > 85 && walkingOrRunningProb == null)
                }
            }
        }
    }

    private fun getPendingIntent(context: WalkingForegroundService): PendingIntent {
        val intent = Intent(context, WalkingForegroundService::class.java).apply {
            action = WalkingForegroundService.ACTION_ACTIVITY_RECOGNITION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(context, 100, intent, flags)
    }
}
