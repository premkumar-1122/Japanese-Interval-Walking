package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "walking_sessions")
data class WalkingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val durationSeconds: Long,
    val steps: Int,
    val calories: Double,
    val avgCadence: Double,
    val avgPace: Double, // in minutes per kilometer, e.g. 7.5 meaning 7m 30s per km
    val slowCyclesCount: Int,
    val fastCyclesCount: Int,
    val totalCycles: Int,
    val isSyncedToGoogleFit: Boolean = false,
    val isSyncedToHealthConnect: Boolean = false
) : Serializable
