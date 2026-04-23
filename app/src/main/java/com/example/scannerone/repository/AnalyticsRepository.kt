package com.example.scannerone.repository

import com.example.scannerone.database.AnalyticsDao

class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {
    fun getTotalNetworksCount() = analyticsDao.getTotalNetworksCount()

    fun getTotalScansCount() = analyticsDao.getTotalScansCount()

    fun getTotalDistance() = analyticsDao.getTotalDistance()

    fun getTotalTime() = analyticsDao.getTotalTime()

    fun getDiscoveryTrendStats(startTime: Long, endTime: Long, bucketSize: Long) =
        analyticsDao.getDiscoveryTrendStats(startTime, endTime, bucketSize)

    fun getScanTrendStats(startTime: Long, endTime: Long, bucketSize: Long) =
        analyticsDao.getScanTrendStats(startTime, endTime, bucketSize)

    fun getSessionTrendStats(startTime: Long, endTime: Long, bucketSize: Long) =
        analyticsDao.getSessionTrendStats(startTime, endTime, bucketSize)

    fun getSessionWithMostUniqueNetworks() = analyticsDao.getSessionWithMostUniqueNetworks()

    fun getLongestSession() = analyticsDao.getLongestSession()

    fun getMostDistanceSession() = analyticsDao.getMostDistanceSession()

    fun getCategoryStatsFlow(sessionId: Int?) = analyticsDao.getCategoryStatsFlow(sessionId)

    fun getSecurityStatsFlow(sessionId: Int?) = analyticsDao.getSecurityStatsFlow(sessionId)

    fun getFrequencyStatsFlow(sessionId: Int?) = analyticsDao.getFrequencyStatsFlow(sessionId)
}
