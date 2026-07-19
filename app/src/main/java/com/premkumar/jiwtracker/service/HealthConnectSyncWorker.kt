package com.premkumar.jiwtracker.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import com.premkumar.jiwtracker.data.AppDatabase
import com.premkumar.jiwtracker.data.WalkingRepository
import com.premkumar.jiwtracker.data.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class HealthConnectSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val tag = "HealthConnectSyncWorker"

    override suspend fun doWork(): Result {
        Log.i(tag, "Health Connect Sync Working starting background processing...")

        val db = AppDatabase.getDatabase(applicationContext)
        val repo = WalkingRepository(db.walkingSessionDao())
        val manager = HealthConnectManager(applicationContext)

        if (!manager.isSdkAvailable()) {
            Log.w(tag, "Sync skipped: Health Connect is not available on this device.")
            return Result.failure()
        }

        if (!manager.hasAllPermissions()) {
            Log.w(tag, "Sync skipped: App does not have necessary permissions granted.")
            return Result.failure()
        }

        // Get all database sessions and find those requiring sync
        val allSessions = repo.allSessions.first()
        val unsyncedSessions = allSessions.filter { !it.isSyncedToHealthConnect }

        if (unsyncedSessions.isEmpty()) {
            Log.i(tag, "No unsynced walking sessions found. Sync completed successfully.")
            return Result.success()
        }

        Log.i(tag, "Found ${unsyncedSessions.size} unsynced sessions to process.")
        var hasTransientFailure = false

        for (session in unsyncedSessions) {
            // Check condition for phantom/zero/short test steps
            if (session.steps <= 0 || session.calories <= 0.0 || session.durationSeconds < 10) {
                Log.i(tag, "Skipping phantom/invalid session ID ${session.id} to avoid bad sync. Stats (Steps: ${session.steps}, Calories: ${session.calories}, Duration: ${session.durationSeconds}s)")
                // Mark as synced to prevent retrying this invalid record forever
                repo.updateSyncStatus(session.id, googleFit = session.isSyncedToGoogleFit, healthConnect = true)
                continue
            }

            // Sync structured walking session
            val isSuccess = manager.writeSession(session)
            if (isSuccess) {
                Log.i(tag, "Session ID ${session.id} synced successfully.")
                repo.updateSyncStatus(session.id, googleFit = session.isSyncedToGoogleFit, healthConnect = true)
            } else {
                Log.e(tag, "Session ID ${session.id} failed to sync (could be transient).")
                hasTransientFailure = true
            }
        }

        return if (hasTransientFailure) {
            Log.w(tag, "Transient sync failures encountered. Requesting retry.")
            Result.retry()
        } else {
            Log.i(tag, "All eligible sessions processed successfully.")
            Result.success()
        }
    }

    companion object {
        fun enqueueSync(context: Context) {
            try {
                val syncRequest = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10L, // Backoff duration
                        TimeUnit.SECONDS
                    )
                    .addTag("HealthConnectSyncWorker")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "health_connect_sync_work",
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
                Log.i("HealthConnectSyncWorker", "Successfully enqueued unique background sync work.")
            } catch (e: Exception) {
                Log.e("HealthConnectSyncWorker", "Failed to enqueue Health Connect background sync.", e)
            }
        }
    }
}
