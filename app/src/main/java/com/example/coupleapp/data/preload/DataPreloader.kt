package com.example.coupleapp.data.preload

import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.data.repository.MissingCacheRepository
import com.example.coupleapp.data.repository.ProfileCacheRepository
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * DataPreloader - Preloads data for important screens in the background.
 * 
 * This improves UX by loading data before user navigates to specific screens,
 * so when they arrive, data is already cached and displays instantly.
 * 
 * Preloading Strategy:
 * 1. On Home screen load: Preload Missing data (most used feature)
 * 2. Background: Don't block UI, just cache for later
 * 3. Skip if already cached: Check cache freshness before fetching
 */
object DataPreloader {
    
    private const val TAG = "DataPreloader"
    
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val missingCacheRepository = MissingCacheRepository.getInstance()
    private val profileCacheRepository = ProfileCacheRepository.getInstance()
    
    private var preloadJob: Job? = null
    private var isPreloading = false
    
    /**
     * Preload all important data in the background.
     * Called from HomeViewModel when user enters Home screen.
     * 
     * This is fire-and-forget - doesn't return any result.
     */
    fun preloadInBackground(scope: CoroutineScope) {
        if (isPreloading) {
            Log.d(TAG, "⏳ Already preloading, skipping...")
            return
        }
        
        preloadJob?.cancel()
        preloadJob = scope.launch(Dispatchers.IO) {
            isPreloading = true
            try {
                Log.d(TAG, "🚀 Starting background preload...")
                
                // Preload Missing feature data
                preloadMissingData()
                
                Log.d(TAG, "✅ Background preload completed!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during preload", e)
            } finally {
                isPreloading = false
            }
        }
    }
    
    /**
     * Preload Missing feature data:
     * 1. Check if profile cache exists
     * 2. If not, fetch from Firebase and cache
     * 3. Check if missing cache is fresh
     * 4. If not, fetch missing data and cache
     */
    private suspend fun preloadMissingData() {
        try {
            val firebaseUser = authRepository.currentUser
            if (firebaseUser == null) {
                Log.d(TAG, "User not logged in, skipping Missing preload")
                return
            }
            
            val userId = firebaseUser.uid
            Log.d(TAG, "📦 Preloading Missing data for user: $userId")
            
            // ========== Step 1: Check/Load Profile Cache ==========
            var cachedCurrentUser = profileCacheRepository.getCachedCurrentUser()
            var cachedPartner = profileCacheRepository.getCachedPartner()
            
            if (cachedCurrentUser == null || cachedCurrentUser.id != userId) {
                Log.d(TAG, "🔄 Profile cache missing, fetching from Firebase...")
                
                // Fetch current user
                val currentUserResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )
                
                cachedCurrentUser = currentUserResult.getOrNull()
                if (cachedCurrentUser != null) {
                    profileCacheRepository.cacheCurrentUser(cachedCurrentUser)
                    Log.d(TAG, "✅ Cached current user profile: ${cachedCurrentUser.displayName}")
                    
                    // Fetch partner if exists
                    val partnerId = cachedCurrentUser.partnerId
                    if (!partnerId.isNullOrEmpty()) {
                        val partnerResult = firestoreRepository.getDocument(
                            "users",
                            partnerId,
                            FirebaseUser::class.java
                        )
                        cachedPartner = partnerResult.getOrNull()
                        if (cachedPartner != null) {
                            profileCacheRepository.cachePartner(cachedPartner)
                            Log.d(TAG, "✅ Cached partner profile: ${cachedPartner.displayName}")
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to fetch current user from Firebase")
                    return
                }
            } else {
                Log.d(TAG, "📦 Profile cache already exists: ${cachedCurrentUser.displayName}")
            }
            
            // ========== Step 2: Check/Load Missing Cache ==========
            val partnerId = cachedCurrentUser?.partnerId
            if (partnerId.isNullOrEmpty()) {
                Log.d(TAG, "No partner linked, skipping Missing data preload")
                return
            }
            
            val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
            
            // Check if cache is fresh
            val isCacheFresh = missingCacheRepository.isCacheFresh(coupleId)
            val hasCompleteCache = missingCacheRepository.hasCompleteCache(coupleId)
            
            if (isCacheFresh && hasCompleteCache) {
                Log.d(TAG, "📦 Missing cache is FRESH and COMPLETE, skipping preload")
                return
            }
            
            Log.d(TAG, "🔄 Missing cache stale/incomplete, fetching from Firebase...")
            
            // ========== Step 3: Fetch Missing Data ==========
            val today = LocalDate.now()
            val historyDays = 7
            
            // Convert FirebaseUser to UserProfile
            val currentUserProfile = UserProfile(
                id = cachedCurrentUser.id,
                name = cachedCurrentUser.displayName,
                avatarUrl = cachedCurrentUser.profileImageUrl.takeIf { it.isNotEmpty() }
            )
            
            val partnerProfile = cachedPartner?.let {
                UserProfile(
                    id = it.id,
                    name = it.displayName,
                    avatarUrl = it.profileImageUrl.takeIf { url -> url.isNotEmpty() }
                )
            }
            
            // Cache profiles to MissingCacheRepository
            missingCacheRepository.cacheProfiles(coupleId, currentUserProfile, partnerProfile)
            
            // Fetch history for last 7 days
            val histories = mutableListOf<DailyMissingHistory>()
            var myTodayCount = 0
            var partnerTodayCount = 0
            
            for (dayOffset in 0 until historyDays) {
                val date = today.minusDays(dayOffset.toLong())
                val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Load both users' counts for this day
                val myCount = loadTodayCount(coupleId, userId, dateString)
                val partnerCount = loadTodayCount(coupleId, partnerId, dateString)
                
                // Track today's counts
                if (dayOffset == 0) {
                    myTodayCount = myCount
                    partnerTodayCount = partnerCount
                }
                
                // Only add day if at least one person has > 0 hearts
                if (myCount > 0 || partnerCount > 0) {
                    val summaries = listOf(
                        DailyMissingSummary(
                            date = date,
                            userId = userId,
                            userName = currentUserProfile.name,
                            userAvatar = currentUserProfile.avatarUrl,
                            missCount = myCount
                        ),
                        DailyMissingSummary(
                            date = date,
                            userId = partnerId,
                            userName = partnerProfile?.name ?: "",
                            userAvatar = partnerProfile?.avatarUrl,
                            missCount = partnerCount
                        )
                    )
                    histories.add(DailyMissingHistory(date = date, summaries = summaries))
                }
            }
            
            // Cache history
            missingCacheRepository.cacheMissingHistory(coupleId, histories)
            
            // Calculate and cache summary
            val totalMissing = histories.sumOf { day ->
                day.summaries.sumOf { it.missCount }
            }
            val (currentStreak, longestStreak) = calculateStreak(histories)
            missingCacheRepository.cacheSummary(coupleId, currentStreak, longestStreak, totalMissing)
            
            // Cache today's counts
            missingCacheRepository.updateTodayCount(
                coupleId = coupleId,
                userId = userId,
                userName = currentUserProfile.name,
                userAvatarUrl = currentUserProfile.avatarUrl,
                newCount = myTodayCount
            )
            
            if (partnerProfile != null) {
                missingCacheRepository.updateTodayCount(
                    coupleId = coupleId,
                    userId = partnerId,
                    userName = partnerProfile.name,
                    userAvatarUrl = partnerProfile.avatarUrl,
                    newCount = partnerTodayCount
                )
            }
            
            Log.d(TAG, "✅ Missing data preloaded: ${histories.size} days, me=$myTodayCount, partner=$partnerTodayCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error preloading Missing data", e)
        }
    }
    
    /**
     * Load count for a specific date
     */
    private suspend fun loadTodayCount(coupleId: String, userId: String, date: String): Int {
        val recordId = "${coupleId}_${userId}_$date"
        
        val result = firestoreRepository.getDocument(
            "missing_records",
            recordId,
            FirebaseMissingRecord::class.java
        )
        
        return result.getOrNull()?.count ?: 0
    }
    
    /**
     * Calculate streak from history
     */
    private fun calculateStreak(history: List<DailyMissingHistory>): Pair<Int, Int> {
        if (history.isEmpty()) return Pair(0, 0)
        
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 0
        var lastDate: LocalDate? = null
        
        val sortedHistory = history.sortedByDescending { it.date }
        
        for (day in sortedHistory) {
            val bothSent = day.summaries.all { it.missCount > 0 }
            
            if (bothSent) {
                if (lastDate == null) {
                    tempStreak = 1
                } else {
                    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(day.date, lastDate)
                    if (daysDiff == 1L) {
                        tempStreak++
                    } else {
                        longestStreak = maxOf(longestStreak, tempStreak)
                        tempStreak = 1
                    }
                }
                lastDate = day.date
            } else {
                if (tempStreak > 0) {
                    if (currentStreak == 0) {
                        currentStreak = tempStreak
                    }
                    longestStreak = maxOf(longestStreak, tempStreak)
                    tempStreak = 0
                    lastDate = null
                }
            }
        }
        
        longestStreak = maxOf(longestStreak, tempStreak)
        if (currentStreak == 0) {
            currentStreak = tempStreak
        }
        
        return Pair(currentStreak, longestStreak)
    }
    
    /**
     * Cancel any ongoing preload operations
     */
    fun cancelPreload() {
        preloadJob?.cancel()
        isPreloading = false
    }
}
