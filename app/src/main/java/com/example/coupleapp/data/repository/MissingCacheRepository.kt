package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.local.CoupleAppDatabase
import com.example.coupleapp.data.local.dao.MissingDao
import com.example.coupleapp.data.local.entity.MissingEntity
import com.example.coupleapp.data.local.entity.MissingSummaryEntity
import com.example.coupleapp.data.local.entity.MissingUserProfileEntity
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for caching Missing data using Room Database.
 * 
 * Strategy: Cache-First with Background Refresh
 * 1. On screen open: Load from cache immediately (instant UI)
 * 2. If cache exists: Show cached data, then refresh from server in background
 * 3. If cache empty/stale: Show loading, fetch from server, cache result
 * 
 * Cache Freshness:
 * - Data is considered "fresh" if synced within last 5 minutes
 * - Stale data is still shown immediately, but triggers background refresh
 * - Day change forces a full refresh
 */
class MissingCacheRepository(context: Context = CoupleApplication.instance) {
    
    companion object {
        private const val TAG = "MissingCacheRepo"
        
        // Cache is considered fresh for 30 minutes
        // This balances between showing instant data and keeping data relatively up-to-date
        const val CACHE_FRESHNESS_MS = 30 * 60 * 1000L // 30 minutes
        
        // Keep history for last 7 days
        const val HISTORY_DAYS = 7
        
        @Volatile
        private var INSTANCE: MissingCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): MissingCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MissingCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val database = CoupleAppDatabase.getInstance(context)
    private val missingDao: MissingDao = database.missingDao()
    
    // ============ Cache Read Operations ============
    
    /**
     * Check if we have cached data for a couple
     */
    suspend fun hasCachedData(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        val history = missingDao.getMissingHistory(coupleId, 1)
        history.isNotEmpty()
    }
    
    /**
     * Check if cache has COMPLETE data (at least 2 different dates)
     * This prevents showing incomplete cache (only today, missing yesterday)
     */
    suspend fun hasCompleteCache(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val history = missingDao.getMissingHistory(coupleId, HISTORY_DAYS)
            // Get unique dates
            val uniqueDates = history.map { it.date }.distinct()
            // Cache is complete if we have at least 2 different dates OR we have profiles cached
            val hasMultipleDays = uniqueDates.size >= 2
            val hasProfiles = missingDao.getCurrentUserProfile(coupleId) != null
            
            Log.d(TAG, "hasCompleteCache: uniqueDates=${uniqueDates.size}, hasProfiles=$hasProfiles")
            hasMultipleDays && hasProfiles
        } catch (e: Exception) {
            Log.e(TAG, "Error checking complete cache", e)
            false
        }
    }
    
    /**
     * Check if we have cached data for TODAY (more precise check)
     * Returns true only if there's recent data (within 24 hours) for today
     */
    suspend fun hasTodayData(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val todayEntries = missingDao.getMissingByDateOnly(coupleId, today)
            
            // Check if we have profile cache too
            val hasProfiles = missingDao.getCurrentUserProfile(coupleId) != null
            
            // If we have today's entries OR just profiles (for new day with no sends yet)
            val hasData = todayEntries.isNotEmpty() || hasProfiles
            
            Log.d(TAG, "hasTodayData check: hasEntries=${todayEntries.isNotEmpty()}, hasProfiles=$hasProfiles")
            hasData
        } catch (e: Exception) {
            Log.e(TAG, "Error checking today's data", e)
            false
        }
    }
    
    /**
     * Check if cache is fresh (synced within threshold)
     */
    suspend fun isCacheFresh(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - CACHE_FRESHNESS_MS
        missingDao.isCacheFresh(coupleId, threshold)
    }
    
    /**
     * Get last sync time
     */
    suspend fun getLastSyncTime(coupleId: String): Long? = withContext(Dispatchers.IO) {
        missingDao.getLastSyncTime(coupleId)
    }
    
    /**
     * Load cached user profiles
     */
    suspend fun getCachedProfiles(coupleId: String): Pair<UserProfile?, UserProfile?> = withContext(Dispatchers.IO) {
        val currentUserEntity = missingDao.getCurrentUserProfile(coupleId)
        val partnerEntity = missingDao.getPartnerProfile(coupleId)
        
        val currentUser = currentUserEntity?.let {
            UserProfile(
                id = it.userId,
                name = it.userName,
                avatarUrl = it.userAvatarUrl
            )
        }
        
        val partner = partnerEntity?.let {
            UserProfile(
                id = it.userId,
                name = it.userName,
                avatarUrl = it.userAvatarUrl
            )
        }
        
        Pair(currentUser, partner)
    }
    
    /**
     * Load cached missing history and convert to domain models
     */
    suspend fun getCachedHistory(
        coupleId: String,
        currentUserId: String
    ): List<DailyMissingHistory> = withContext(Dispatchers.IO) {
        val entities = missingDao.getMissingHistory(coupleId, HISTORY_DAYS)
        val history = convertEntitiesToHistory(entities, currentUserId)
        Log.d(TAG, "Loaded ${entities.size} entities -> ${history.size} days of history from cache")
        history
    }
    
    /**
     * Get today's count for a user from cache
     */
    suspend fun getCachedTodayCount(
        coupleId: String,
        userId: String,
        userName: String,
        userAvatarUrl: String?
    ): UserMissCount = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val count = missingDao.getTodayCount(coupleId, userId, today) ?: 0
        
        UserMissCount(
            userId = userId,
            userName = userName,
            userAvatar = userAvatarUrl,
            todayCount = count
        )
    }
    
    /**
     * Get cached summary
     */
    suspend fun getCachedSummary(coupleId: String): MissingSummaryEntity? = withContext(Dispatchers.IO) {
        missingDao.getSummary(coupleId)
    }
    
    // ============ Cache Write Operations ============
    
    /**
     * Save user profiles to cache
     */
    suspend fun cacheProfiles(
        coupleId: String,
        currentUser: UserProfile,
        partner: UserProfile?
    ) = withContext(Dispatchers.IO) {
        val profiles = mutableListOf<MissingUserProfileEntity>()
        
        profiles.add(MissingUserProfileEntity(
            coupleId = coupleId,
            userId = currentUser.id,
            userName = currentUser.name,
            userAvatarUrl = currentUser.avatarUrl,
            isCurrentUser = true
        ))
        
        partner?.let {
            profiles.add(MissingUserProfileEntity(
                coupleId = coupleId,
                userId = it.id,
                userName = it.name,
                userAvatarUrl = it.avatarUrl,
                isCurrentUser = false
            ))
        }
        
        missingDao.insertOrUpdateProfiles(profiles)
        Log.d(TAG, "Cached ${profiles.size} user profiles for couple $coupleId")
    }
    
    /**
     * Save missing history to cache
     */
    suspend fun cacheMissingHistory(
        coupleId: String,
        history: List<DailyMissingHistory>
    ) = withContext(Dispatchers.IO) {
        val entities = history.flatMap { day ->
            day.summaries.map { summary ->
                MissingEntity(
                    coupleId = coupleId,
                    userId = summary.userId,
                    userName = summary.userName,
                    userAvatarUrl = summary.userAvatar,
                    date = day.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    missCount = summary.missCount
                )
            }
        }
        
        if (entities.isNotEmpty()) {
            missingDao.insertOrUpdateAll(entities)
            Log.d(TAG, "Cached ${entities.size} missing records for couple $coupleId")
        }
    }
    
    /**
     * Save summary to cache
     */
    suspend fun cacheSummary(
        coupleId: String,
        currentStreak: Int,
        longestStreak: Int,
        totalMissCount: Int
    ) = withContext(Dispatchers.IO) {
        val summary = MissingSummaryEntity(
            coupleId = coupleId,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalMissCount = totalMissCount
        )
        missingDao.insertOrUpdateSummary(summary)
        Log.d(TAG, "Cached summary for couple $coupleId: streak=$currentStreak")
    }
    
    /**
     * Update today's count in cache (optimistic update)
     */
    suspend fun updateTodayCount(
        coupleId: String,
        userId: String,
        userName: String,
        userAvatarUrl: String?,
        newCount: Int
    ) = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val existing = missingDao.getMissingByDate(coupleId, userId, today)
        
        if (existing != null) {
            missingDao.updateMissCount(coupleId, userId, today, newCount)
        } else {
            // Create new record for today
            missingDao.insertOrUpdate(MissingEntity(
                coupleId = coupleId,
                userId = userId,
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                date = today,
                missCount = newCount
            ))
        }
        Log.d(TAG, "Updated cache: $userId count=$newCount for $today")
    }
    
    // ============ Cache Maintenance ============
    
    /**
     * Clean up old cached data (older than HISTORY_DAYS)
     */
    suspend fun cleanupOldData() = withContext(Dispatchers.IO) {
        val oldestDate = LocalDate.now()
            .minusDays(HISTORY_DAYS.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        missingDao.deleteOldData(oldestDate)
        Log.d(TAG, "Cleaned up data older than $oldestDate")
    }
    
    /**
     * Clear all cache for a couple (on logout/unlink)
     */
    suspend fun clearCache(coupleId: String) = withContext(Dispatchers.IO) {
        missingDao.deleteAllForCouple(coupleId)
        missingDao.deleteSummary(coupleId)
        missingDao.deleteProfiles(coupleId)
        Log.d(TAG, "Cleared all cache for couple $coupleId")
    }
    
    // ============ Helper Functions ============
    
    /**
     * Convert cached entities to domain models grouped by date
     */
    private fun convertEntitiesToHistory(
        entities: List<MissingEntity>,
        currentUserId: String
    ): List<DailyMissingHistory> {
        return entities
            .groupBy { it.date }
            .map { (dateStr, dayEntities) ->
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val summaries = dayEntities
                    .sortedByDescending { it.userId == currentUserId } // Current user first
                    .map { entity ->
                        DailyMissingSummary(
                            date = date,
                            userId = entity.userId,
                            userName = entity.userName,
                            userAvatar = entity.userAvatarUrl,
                            missCount = entity.missCount
                        )
                    }
                DailyMissingHistory(date = date, summaries = summaries)
            }
            .sortedByDescending { it.date }
    }
}
