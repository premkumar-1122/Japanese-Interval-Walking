package com.premkumar.jiwtracker.data

import kotlinx.coroutines.flow.Flow

class WalkingRepository(private val walkingSessionDao: WalkingSessionDao) {
    
    val allSessions: Flow<List<WalkingSession>> = walkingSessionDao.getAllSessions()

    suspend fun insertSession(session: WalkingSession): Long {
        return walkingSessionDao.insertSession(session)
    }

    suspend fun deleteSession(session: WalkingSession) {
        walkingSessionDao.deleteSession(session)
    }

    suspend fun updateSyncStatus(id: Long, googleFit: Boolean, healthConnect: Boolean) {
        walkingSessionDao.updateSyncStatus(id, googleFit, healthConnect)
    }

    suspend fun clearAll() {
        walkingSessionDao.clearAll()
    }
}
