package com.example.scannerone.repository

import com.example.scannerone.database.SessionDao
import com.example.scannerone.entities.ScanSession

class SessionRepository(private val sessionDao: SessionDao) {
    fun getAllSessions() = sessionDao.getAllSessions()

    fun getScanRecordsForSession(sessionId: Int?) = sessionDao.getScanRecordsForSession(sessionId)

    suspend fun insertSession(session: ScanSession): Long = sessionDao.insertSession(session)

    suspend fun updateSession(session: ScanSession) = sessionDao.updateSession(session)

    suspend fun updateSessionDistance(sessionId: Int, distance: Double) {
        sessionDao.updateSessionDistance(sessionId, distance)
    }

    suspend fun getNetworksFoundInSession(sessionId: Int): Int = sessionDao.getNetworksFoundInSession(sessionId)

    fun getSessionTotalScansCountFlow(sessionId: Int?) = sessionDao.getSessionTotalScansCountFlow(sessionId)

    fun getSessionDiscoveryCountFlow(sessionId: Int?) = sessionDao.getSessionDiscoveryCountFlow(sessionId)

    fun getSessionUniqueNetworksCountFlow(sessionId: Int?) = sessionDao.getSessionUniqueNetworksCountFlow(sessionId)
}
