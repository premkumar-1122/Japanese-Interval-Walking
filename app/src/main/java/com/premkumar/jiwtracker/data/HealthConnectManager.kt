package com.premkumar.jiwtracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first

class HealthConnectManager(private val context: Context) {

    private val tag = "HealthConnectManager"

    // Lazy initialization of HealthConnectClient
    val healthConnectClient: HealthConnectClient? by lazy {
        if (isSdkAvailable()) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                Log.e(tag, "Error creating HealthConnectClient", e)
                null
            }
        } else {
            null
        }
    }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    private var cachedStatus: Int? = null

    fun invalidateCachedStatus() {
        cachedStatus = null
    }

    private fun isProviderPackageInstalled(context: Context): Boolean {
        val providerPackageName = "com.google.android.apps.healthdata"
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(providerPackageName, 0)
            val isEnabled = info.applicationInfo?.enabled == true
            Log.d("HealthConnect", "Provider package info retrieved: ${info.packageName}, enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.d("HealthConnect", "Provider package check failed: ${e.message}")
            false
        }
    }

    private fun getProviderVersion(context: Context): Long {
        val providerPackageName = "com.google.android.apps.healthdata"
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(providerPackageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            -1L
        }
    }

    fun isSdkAvailable(): Boolean {
        return getSdkStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    fun getSdkStatus(): Int {
        val cached = cachedStatus
        if (cached != null) return cached

        val status = HealthConnectClient.getSdkStatus(context)
        val installed = isProviderPackageInstalled(context)
        val version = getProviderVersion(context)
        Log.d("HealthConnect", "SDK Status: $status")
        Log.d("HealthConnect", "Android Version: ${android.os.Build.VERSION.SDK_INT}")
        Log.d("HealthConnect", "Provider Installed: $installed")
        Log.d("HealthConnect", "Provider Version: $version")

        var finalStatus = status

        if (status != HealthConnectClient.SDK_AVAILABLE && installed) {
            try {
                // Test client initialization directly to see if standalone provider works fine
                val testClient = HealthConnectClient.getOrCreate(context)
                Log.d("HealthConnect", "Fallback check success: client initialized successfully! Setting status to SDK_AVAILABLE")
                finalStatus = HealthConnectClient.SDK_AVAILABLE
            } catch (e: Exception) {
                Log.e("HealthConnect", "Fallback client initialization failed for status $status", e)
            }
        }

        cachedStatus = finalStatus
        return finalStatus
    }

    fun getPlayStoreIntent(): Intent {
        val providerPackageName = "com.google.android.apps.healthdata"
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$providerPackageName")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            Log.e(tag, "Failed to check permissions", e)
            false
        }
    }

    suspend fun writeSession(session: WalkingSession): Boolean {
        val client = healthConnectClient ?: run {
            Log.e(tag, "Sync failed: Health Connect Client is unavailable.")
            return false
        }

        if (!hasAllPermissions()) {
            Log.w(tag, "Sync skipped: Missing permissions.")
            return false
        }

        // Validate session. Do not sync if phantom, empty, or zero.
        if (session.steps <= 0 || session.calories <= 0.0 || session.durationSeconds < 10) {
            Log.w(tag, "Sync skipped: Invalid/empty session data. Steps: ${session.steps}, Calories: ${session.calories}, Duration: ${session.durationSeconds}")
            return false
        }

        return try {
            val startTime = Instant.ofEpochMilli(session.dateMillis)
            val endTime = startTime.plusSeconds(session.durationSeconds)
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

            Log.d(tag, "Starting Health Connect sync for Session ID ${session.id}, Steps: ${session.steps}, Calories: ${session.calories}")

            // 1. Write the Walking Exercise Session
            val exerciseRecord = ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                title = "Japanese Interval Walking",
                notes = "Slow/Fast Cycles: ${session.slowCyclesCount}/${session.fastCyclesCount}",
                metadata = androidx.health.connect.client.records.metadata.Metadata()
            )

            // 2. Write the Steps Record
            val stepsRecord = StepsRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                count = session.steps.toLong(),
                metadata = androidx.health.connect.client.records.metadata.Metadata()
            )

            // 3. Write Calories Record (derived from calories metric)
            val caloriesRecord = ActiveCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                energy = Energy.kilocalories(session.calories),
                metadata = androidx.health.connect.client.records.metadata.Metadata()
            )

            // 4. Write Distance Record (stride length estimate: ~0.72m per step)
            val estimatedDistanceMeters = session.steps * 0.72
            val distanceRecord = DistanceRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                distance = Length.meters(estimatedDistanceMeters),
                metadata = androidx.health.connect.client.records.metadata.Metadata()
            )

            // Deliver batches to Health Connect
            val response = client.insertRecords(
                listOf<androidx.health.connect.client.records.Record>(
                    exerciseRecord,
                    stepsRecord,
                    caloriesRecord,
                    distanceRecord
                )
            )

            Log.i(tag, "Successfully wrote records to Health Connect. Record IDs: ${response.recordIdsList}")
            
            // Save sync metrics to preferences
            saveSyncMetadata(session.dateMillis, session.steps, session.calories)

            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to write session ${session.id} to Health Connect", e)
            false
        }
    }

    private fun saveSyncMetadata(timestamp: Long, steps: Int, calories: Double) {
        val sharedPrefs = context.getSharedPreferences("health_connect_sync_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putLong("last_sync_timestamp", timestamp)
            putInt("last_synced_step_count", steps)
            putFloat("last_synced_calories", calories.toFloat())
            apply()
        }
        Log.d(tag, "Saved sync metadata: Timestamp: $timestamp, Steps: $steps, Calories: $calories")
    }

    fun getSyncMetadata(): SyncMetadata {
        val sharedPrefs = context.getSharedPreferences("health_connect_sync_prefs", Context.MODE_PRIVATE)
        return SyncMetadata(
            lastSyncTimestamp = sharedPrefs.getLong("last_sync_timestamp", 0L),
            lastSyncedStepCount = sharedPrefs.getInt("last_synced_step_count", 0),
            lastSyncedCalories = sharedPrefs.getFloat("last_synced_calories", 0f).toDouble()
        )
    }

    suspend fun readStatsForPeriod(startTime: Instant, endTime: Instant): Pair<Long, Double> {
        val client = healthConnectClient ?: return Pair(0L, 0.0)
        if (!hasAllPermissions()) return Pair(0L, 0.0)

        return try {
            val stepsRequest = androidx.health.connect.client.request.ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(startTime, endTime)
            )
            val stepsResponse = client.readRecords(stepsRequest)
            val stepsCount = stepsResponse.records.sumOf { it.count }

            val caloriesRequest = androidx.health.connect.client.request.ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(startTime, endTime)
            )
            val caloriesResponse = client.readRecords(caloriesRequest)
            val totalCalories = caloriesResponse.records.sumOf { it.energy.inKilocalories }

            Log.i(tag, "Read back from Health Connect: Steps: $stepsCount, Calories: $totalCalories")
            Pair(stepsCount, totalCalories)
        } catch (e: Exception) {
            Log.e(tag, "Error reading stats from Health Connect", e)
            Pair(0L, 0.0)
        }
    }
}

data class SyncMetadata(
    val lastSyncTimestamp: Long,
    val lastSyncedStepCount: Int,
    val lastSyncedCalories: Double
)
