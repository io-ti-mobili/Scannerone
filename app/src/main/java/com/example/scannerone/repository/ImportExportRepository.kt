package com.example.scannerone.repository

import androidx.room.withTransaction
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.database.ImportExportDao
import com.example.scannerone.database.NetworkDao
import com.example.scannerone.database.SessionDao
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.io.ExportBundle

class ImportExportRepository(
    private val importExportDao: ImportExportDao,
    private val networkDao: NetworkDao,
    private val sessionDao: SessionDao
) {
    fun getNetworksSequence(pageSize: Int = 200): Sequence<WifiNetwork> = sequence {
        var offset = 0
        while (true) {
            val page = importExportDao.getNetworksPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }

    fun getSessionsSequence(pageSize: Int = 200): Sequence<ScanSession> = sequence {
        var offset = 0
        while (true) {
            val page = importExportDao.getSessionsPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }

    fun getRecordsSequence(pageSize: Int = 200): Sequence<WifiScanRecord> = sequence {
        var offset = 0
        while (true) {
            val page = importExportDao.getRecordsPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }

    suspend fun deleteAllRecords() = importExportDao.deleteAllRecords()

    suspend fun deleteAllSessions() = importExportDao.deleteAllSessions()

    suspend fun deleteAllNetworks() = importExportDao.deleteAllNetworks()

    suspend fun importMergeBundle(bundle: ExportBundle, db: AppDatabase) {
        db.withTransaction {
            val networkIdMap = mutableMapOf<Int, Int>()
            bundle.networks?.forEach { importedNetwork ->
                val rowId = networkDao.insertNetwork(importedNetwork.copy(id = 0))
                val actualId = if (rowId == -1L) {
                    networkDao.getNetworkIdByBssid(importedNetwork.bssid)
                        ?: error("BSSID ${importedNetwork.bssid}: inserimento ignorato ma ID non trovato")
                } else {
                    rowId.toInt()
                }
                networkIdMap[importedNetwork.id] = actualId
            }

            val sessionIdMap = mutableMapOf<Int, Int>()
            bundle.sessions?.forEach { importedSession ->
                val newId = sessionDao.insertSession(importedSession.copy(id = 0))
                sessionIdMap[importedSession.id] = newId.toInt()
            }

            bundle.records?.chunked(200)?.forEach { chunk ->
                val remapped = chunk.map { record ->
                    record.copy(
                        id = 0,
                        networkId = networkIdMap[record.networkId] ?: 0,
                        sessionId = record.sessionId?.let { sessionIdMap[it] }
                    )
                }
                importExportDao.insertRecords(remapped)
            }
        }
    }
}
