package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_preferences")

class OnboardingPrefs(private val context: Context) {

    companion object {
        const val TAG = "OnboardingPrefs"
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val USER_WEIGHT = floatPreferencesKey("onboarding_user_weight")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.onboardingDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    val userWeight: Flow<Float?> = context.onboardingDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_WEIGHT]
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        try {
            context.onboardingDataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED] = completed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write onboarding completed state", e)
        }
    }

    suspend fun setUserWeight(weight: Float) {
        try {
            context.onboardingDataStore.edit { preferences ->
                preferences[USER_WEIGHT] = weight
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write user weight", e)
        }
    }

    suspend fun resetOnboarding() {
        try {
            context.onboardingDataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED] = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset onboarding", e)
        }
    }
}

object OnboardingAnalytics {
    private const val TAG = "OnboardingAnalytics"

    fun logEvent(event: String, params: Map<String, Any> = emptyMap()) {
        val paramStr = if (params.isNotEmpty()) {
            params.entries.joinToString(prefix = " {", postfix = "}") { "${it.key}=${it.value}" }
        } else {
            ""
        }
        Log.i(TAG, "ANALYTICS EVENT: $event$paramStr")
    }
}
