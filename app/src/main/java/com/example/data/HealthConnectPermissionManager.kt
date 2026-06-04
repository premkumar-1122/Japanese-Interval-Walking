package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord

class HealthConnectPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectPermission"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }

    private val healthConnectManager = HealthConnectManager(context)

    // Centralized access to the required permission set
    val requiredPermissions = healthConnectManager.requiredPermissions

    /**
     * Check if all required Health Connect permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean {
        Log.d(TAG, "Checking all permissions...")
        val client = healthConnectManager.healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            Log.d(TAG, "Currently granted permissions: $granted")
            val hasAll = granted.containsAll(requiredPermissions)
            Log.d(TAG, "Has all required permissions: $hasAll")
            hasAll
        } catch (e: Exception) {
            Log.e(TAG, "Error checking granted permissions", e)
            false
        }
    }

    /**
     * Attempts to launch the standard Health Connect permission flow.
     * If launching throws an exception (e.g. ActivityNotFoundException/unsupported on old standalone app systems), 
     * it falls back to launching the rationale list or the app settings for com.google.android.apps.healthdata.
     */
    fun launchPermissionRequestSafely(
        launcher: androidx.activity.result.ActivityResultLauncher<Set<String>>,
        onFailure: (Exception) -> Unit = {}
    ) {
        Log.d(TAG, "Requesting permissions through ActivityResultLauncher")
        try {
            launcher.launch(requiredPermissions)
            Log.d(TAG, "Launcher contract started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Standard Health Connect permission launcher failed", e)
            onFailure(e)
            
            // Try fallback settings/rationale layout action
            try {
                Log.d(TAG, "Attempting Fallback 1: Show Permissions Rationale Activity")
                val intent = Intent("androidx.health.connect.client.ACTION_HEALTH_CONNECT_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Opening Health Connect settings page...", Toast.LENGTH_LONG).show()
            } catch (e1: Exception) {
                Log.e(TAG, "Fallback 1 (Settings intent) failed", e1)
                
                try {
                    Log.d(TAG, "Attempting Fallback 2: Open standalone com.google.android.apps.healthdata App Details Settings")
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", PROVIDER_PACKAGE, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please enable permissions manually in Health Connect app settings.", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback 2 (App details intent) failed", e2)
                    Toast.makeText(context, "Could not open Health Connect permissions layout. Please open the Health Connect app to grant permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
