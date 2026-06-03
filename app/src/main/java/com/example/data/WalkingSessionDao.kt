package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkingSessionDao {
    @Query("SELECT * FROM walking_sessions ORDER BY dateMillis DESC")
    fun getAllSessions(): Flow<List<WalkingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WalkingSession): Long

    @Delete
    suspend fun deleteSession(session: WalkingSession)

    @Query("UPDATE walking_sessions SET isSyncedToGoogleFit = :googleFit, isSyncedToHealthConnect = :healthConnect WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, googleFit: Boolean, healthConnect: Boolean)

    @Query("DELETE FROM walking_sessions")
    suspend fun clearAll()
}
