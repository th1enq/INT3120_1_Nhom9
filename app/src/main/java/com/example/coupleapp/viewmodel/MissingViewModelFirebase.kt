package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.data.repository.MissingCacheRepository
import com.example.coupleapp.widget.WidgetManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel for Missing feature with Firebase integration and local caching.
 * 
 * Cache-First Strategy:
 * 1. On init: Load cached data immediately for instant UI
 * 2. Background sync: Refresh from Firebase in background
 * 3. Result: User sees data instantly, no 5-6s loading wait
 */
class MissingViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val cacheRepository = MissingCacheRepository.getInstance()

    companion object {
        private const val TAG = "MissingViewModel"
    }

    private val _uiState = MutableStateFlow(MissingUiStateFirebase())
    val uiState: StateFlow<MissingUiStateFirebase> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null
    private var animationJob: Job? = null
    private var sendJob: Job? = null
    private var backgroundSyncJob: Job? = null
    
    // Debounce mechanism for rapid clicks
    private var pendingApiCount = 0
    private var lastApiCallTime = 0L
    private val API_DEBOUNCE_MS = 500L // Wait 500ms after last click before calling API
    
    // Track couple ID for caching
    private var currentCoupleId: String = ""

    init {
        Log.d(TAG, "MissingViewModelFirebase initialized")
        loadInitialDataWithCache()
    }

    /**
     * Load initial data with cache-first strategy:
     * 1. Try to load from cache first (instant)
     * 2. If cache exists, show it immediately and sync in background
     * 3. If no cache, fall back to loading from Firebase
     */
    private fun loadInitialDataWithCache() {
        viewModelScope.launch {
            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val userId = firebaseUser.uid
                Log.d(TAG, "Loading data for user: $userId (cache-first)")

                // Load current user info (needed for coupleId)
                val currentUserResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                val currentUser = currentUserResult.getOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "Failed to load current user")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val partnerId = currentUser.partnerId
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "No partner linked")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                // Generate coupleId for caching
                currentCoupleId = listOf(userId, partnerId).sorted().joinToString("_")
                
                // Try to load from cache first
                val hasCached = cacheRepository.hasCachedData(currentCoupleId)
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val lastLoadedDate = _uiState.value.lastLoadedDate
                val isDayChanged = lastLoadedDate.isNotEmpty() && lastLoadedDate != today
                
                if (hasCached && !isDayChanged) {
                    Log.d(TAG, "📦 Cache found! Loading from cache first...")
                    loadFromCacheAndSyncBackground(currentCoupleId, userId, partnerId)
                } else {
                    Log.d(TAG, "🌐 No cache or day changed, loading from Firebase...")
                    loadInitialDataFromFirebase()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in loadInitialDataWithCache", e)
                // Fallback to Firebase loading
                loadInitialDataFromFirebase()
            }
        }
    }
    
    /**
     * Load data from cache immediately, then sync in background
     */
    private suspend fun loadFromCacheAndSyncBackground(
        coupleId: String,
        currentUserId: String,
        partnerId: String
    ) {
        try {
            // Load profiles from cache
            val (cachedCurrentUser, cachedPartner) = cacheRepository.getCachedProfiles(coupleId)
            
            if (cachedCurrentUser != null) {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Load history from cache
                val cachedHistory = cacheRepository.getCachedHistory(coupleId, currentUserId)
                
                // Load today counts from cache
                val myTodayCount = cacheRepository.getCachedTodayCount(
                    coupleId, currentUserId,
                    cachedCurrentUser.name, cachedCurrentUser.avatarUrl
                )
                val partnerTodayCount = cachedPartner?.let {
                    cacheRepository.getCachedTodayCount(coupleId, it.id, it.name, it.avatarUrl)
                } ?: UserMissCount("", "", null, 0)
                
                // Load summary from cache
                val cachedSummary = cacheRepository.getCachedSummary(coupleId)
                
                // Calculate flags
                val mySentToday = myTodayCount.todayCount > 0
                val partnerSentToday = partnerTodayCount.todayCount > 0
                val bothSentToday = mySentToday && partnerSentToday
                
                val summary = MissingSummary(
                    totalMissCount = cachedSummary?.totalMissCount ?: cachedHistory.sumOf { day ->
                        day.summaries.sumOf { it.missCount }
                    },
                    todayMissCount = myTodayCount.todayCount + partnerTodayCount.todayCount,
                    currentStreak = cachedSummary?.currentStreak ?: 0,
                    longestStreak = cachedSummary?.longestStreak ?: 0,
                    hasSentToday = bothSentToday,
                    myTodayCount = myTodayCount.todayCount,
                    partnerTodayCount = partnerTodayCount.todayCount,
                    meSentToday = mySentToday,
                    partnerSentToday = partnerSentToday
                )
                
                // Update UI immediately with cached data
                _uiState.update {
                    it.copy(
                        currentUser = cachedCurrentUser,
                        partnerUser = cachedPartner,
                        dailyHistory = cachedHistory,
                        summary = summary,
                        myTodayCount = myTodayCount,
                        partnerTodayCount = partnerTodayCount,
                        isLoading = false, // UI ready!
                        lastLoadedDate = today
                    )
                }
                
                Log.d(TAG, "✅ Loaded from cache instantly! Starting background sync...")
                
                // Sync in background (don't block UI)
                startBackgroundSync()
                
            } else {
                // Cache corrupted or incomplete, fall back to Firebase
                Log.d(TAG, "⚠️ Cache incomplete, falling back to Firebase")
                loadInitialDataFromFirebase()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from cache", e)
            loadInitialDataFromFirebase()
        }
    }
    
    /**
     * Start background sync to refresh data from Firebase
     */
    private fun startBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = viewModelScope.launch {
            try {
                // Check if cache is still fresh
                val isFresh = cacheRepository.isCacheFresh(currentCoupleId)
                if (isFresh) {
                    Log.d(TAG, "🔄 Cache is fresh, skipping background sync")
                    return@launch
                }
                
                Log.d(TAG, "🔄 Starting background sync from Firebase...")
                loadMissingDataSilent()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in background sync", e)
            }
        }
    }

    /**
     * Original Firebase loading (renamed, used as fallback)
     */
    private fun loadInitialDataFromFirebase() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val userId = firebaseUser.uid
                Log.d(TAG, "Loading data for user: $userId")

                // Load current user
                val currentUserResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                val currentUser = currentUserResult.getOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "Failed to load current user")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                // Convert to UserProfile
                val currentUserProfile = UserProfile(
                    id = currentUser.id,
                    name = currentUser.displayName,
                    avatarUrl = currentUser.profileImageUrl.takeIf { it.isNotEmpty() }
                )

                // Load partner if exists
                val partnerId = currentUser.partnerId
                var partnerProfile: UserProfile? = null

                if (!partnerId.isNullOrEmpty()) {
                    val partnerResult = firestoreRepository.getDocument(
                        "users",
                        partnerId,
                        FirebaseUser::class.java
                    )
                    val partner = partnerResult.getOrNull()
                    if (partner != null) {
                        partnerProfile = UserProfile(
                            id = partner.id,
                            name = partner.displayName,
                            avatarUrl = partner.profileImageUrl.takeIf { it.isNotEmpty() }
                        )
                    }
                    
                    // Cache profiles for next time
                    currentCoupleId = listOf(userId, partnerId).sorted().joinToString("_")
                    cacheRepository.cacheProfiles(currentCoupleId, currentUserProfile, partnerProfile)
                }

                // Update profiles first (but keep isLoading = true)
                _uiState.update {
                    it.copy(
                        currentUser = currentUserProfile,
                        partnerUser = partnerProfile
                        // NOTE: isLoading remains true - will be set to false by loadMissingDataInternal
                    )
                }

                // Load missing data - this will set isLoading = false when complete
                loadMissingDataInternal(currentUserProfile, partnerProfile)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Internal function to load missing data with provided user profiles
     * Called from loadInitialData when profiles are already loaded
     */
    private suspend fun loadMissingDataInternal(
        currentUserProfile: UserProfile,
        partnerProfile: UserProfile?
    ) {
        try {
            val currentUserId = currentUserProfile.id
            val partnerId = partnerProfile?.id

            if (currentUserId.isEmpty() || partnerId == null) {
                Log.d(TAG, "No partner linked, skipping missing data load")
                _uiState.update { it.copy(isLoading = false) }
                return
            }

            // Generate coupleId
            val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            Log.d(TAG, "Loading missing data for coupleId: $coupleId, date: $today")

            // Load today's counts for both users
            val myTodayResult = loadTodayCountWithProfile(coupleId, currentUserId, today, currentUserProfile)
            val partnerTodayResult = loadTodayCountWithProfile(coupleId, partnerId, today, partnerProfile)

            // Load last 7 days history
            val historyList = loadLast7DaysHistoryWithProfiles(coupleId, currentUserId, partnerId, currentUserProfile, partnerProfile)

            // Calculate summary
            val totalMissing = historyList.sumOf { day ->
                day.summaries.sumOf { it.missCount }
            }

            // Calculate streak with longest
            val (currentStreak, longestStreak) = calculateStreakWithLongest(historyList, currentUserId, partnerId)

            // Check if both users sent today
            val mySentToday = myTodayResult.todayCount > 0
            val partnerSentToday = partnerTodayResult.todayCount > 0
            val bothSentToday = mySentToday && partnerSentToday

            Log.d(TAG, "📊 Today stats: me=$mySentToday (${myTodayResult.todayCount}), " +
                    "partner=$partnerSentToday (${partnerTodayResult.todayCount}), " +
                    "bothSentToday=$bothSentToday, streak=$currentStreak")

            val summary = MissingSummary(
                totalMissCount = totalMissing,
                todayMissCount = myTodayResult.todayCount + partnerTodayResult.todayCount,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                hasSentToday = bothSentToday,
                myTodayCount = myTodayResult.todayCount,
                partnerTodayCount = partnerTodayResult.todayCount,
                meSentToday = mySentToday,
                partnerSentToday = partnerSentToday
            )

            _uiState.update {
                it.copy(
                    dailyHistory = historyList,
                    summary = summary,
                    myTodayCount = myTodayResult,
                    partnerTodayCount = partnerTodayResult,
                    isLoading = false,
                    lastLoadedDate = today,
                    pendingHearts = 0
                )
            }
            
            // Cache data for next time (background operation)
            viewModelScope.launch {
                try {
                    cacheRepository.cacheMissingHistory(coupleId, historyList)
                    cacheRepository.cacheSummary(coupleId, currentStreak, longestStreak, totalMissing)
                    Log.d(TAG, "📦 Cached missing data for couple $coupleId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error caching missing data", e)
                }
            }

            Log.d(TAG, "Missing data loaded successfully (initial)")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading missing data (internal)", e)
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /**
     * Load missing data from Firestore
     * Also handles day change detection and resets counts appropriately
     */
    private fun loadMissingData() {
        loadDataJob?.cancel()

        loadDataJob = viewModelScope.launch {
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser?.id

                if (currentUserId.isEmpty() || partnerId == null) {
                    Log.d(TAG, "No partner linked, skipping missing data load")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                // Generate coupleId
                val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // ========== DAY CHANGE DETECTION ==========
                val lastLoadedDate = _uiState.value.lastLoadedDate
                if (lastLoadedDate.isNotEmpty() && lastLoadedDate != today) {
                    Log.d(TAG, "📅 Day changed from $lastLoadedDate to $today - resetting counts")
                    // New day! Reset pending hearts
                    _uiState.update { it.copy(pendingHearts = 0, clickCount = 0) }
                }

                Log.d(TAG, "Loading missing data for coupleId: $coupleId, date: $today")

                // Load today's counts for both users
                val myTodayResult = loadTodayCount(coupleId, currentUserId, today)
                val partnerTodayResult = loadTodayCount(coupleId, partnerId, today)

                // Load last 7 days history
                val historyList = loadLast7DaysHistory(coupleId, currentUserId, partnerId)

                // Calculate summary
                val totalMissing = historyList.sumOf { day ->
                    day.summaries.sumOf { it.missCount }
                }
                
                // Calculate streak with longest
                val (currentStreak, longestStreak) = calculateStreakWithLongest(historyList, currentUserId, partnerId)
                
                // Check if both users sent today - THIS IS THE KEY FOR STREAK ACTIVATION
                // hasSentToday = TRUE only when BOTH users have sent at least 1 heart today
                val mySentToday = myTodayResult.todayCount > 0
                val partnerSentToday = partnerTodayResult.todayCount > 0
                val bothSentToday = mySentToday && partnerSentToday
                
                Log.d(TAG, "📊 Today stats: me=$mySentToday (${myTodayResult.todayCount}), " +
                        "partner=$partnerSentToday (${partnerTodayResult.todayCount}), " +
                        "bothSentToday=$bothSentToday, streak=$currentStreak")

                val summary = MissingSummary(
                    totalMissCount = totalMissing,
                    todayMissCount = myTodayResult.todayCount + partnerTodayResult.todayCount,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    hasSentToday = bothSentToday, // Both must send for streak to be active
                    myTodayCount = myTodayResult.todayCount,
                    partnerTodayCount = partnerTodayResult.todayCount,
                    meSentToday = mySentToday,       // For UI - show if I sent today
                    partnerSentToday = partnerSentToday // For UI - show if partner sent today
                )

                _uiState.update {
                    it.copy(
                        dailyHistory = historyList,
                        summary = summary,
                        myTodayCount = myTodayResult,
                        partnerTodayCount = partnerTodayResult,
                        isLoading = false,
                        lastLoadedDate = today, // Remember current date
                        pendingHearts = 0 // Clear pending after sync with server
                    )
                }
                
                // Cache data for next time (background operation)
                viewModelScope.launch {
                    try {
                        cacheRepository.cacheMissingHistory(coupleId, historyList)
                        cacheRepository.cacheSummary(coupleId, currentStreak, longestStreak, totalMissing)
                        Log.d(TAG, "📦 Cached missing data after loadMissingData")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error caching missing data", e)
                    }
                }

                Log.d(TAG, "Missing data loaded successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading missing data", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Load today's count for a user
     */
    private suspend fun loadTodayCount(coupleId: String, userId: String, date: String): UserMissCount {
        val recordId = "${coupleId}_${userId}_$date"
        
        val result = firestoreRepository.getDocument(
            "missing_records",
            recordId,
            FirebaseMissingRecord::class.java
        )

        val record = result.getOrNull()
        val user = _uiState.value.let {
            if (userId == it.currentUser.id) it.currentUser else it.partnerUser
        }

        return UserMissCount(
            userId = userId,
            userName = user?.name ?: "",
            userAvatar = user?.avatarUrl,
            todayCount = record?.count ?: 0
        )
    }

    /**
     * Load today's count for a user with provided profile (for initial load)
     */
    private suspend fun loadTodayCountWithProfile(
        coupleId: String,
        userId: String,
        date: String,
        userProfile: UserProfile?
    ): UserMissCount {
        val recordId = "${coupleId}_${userId}_$date"
        
        val result = firestoreRepository.getDocument(
            "missing_records",
            recordId,
            FirebaseMissingRecord::class.java
        )

        val record = result.getOrNull()

        return UserMissCount(
            userId = userId,
            userName = userProfile?.name ?: "",
            userAvatar = userProfile?.avatarUrl,
            todayCount = record?.count ?: 0
        )
    }

    /**
     * Load last 7 days history with provided profiles (for initial load)
     */
    private suspend fun loadLast7DaysHistoryWithProfiles(
        coupleId: String,
        currentUserId: String,
        partnerId: String,
        currentUserProfile: UserProfile,
        partnerProfile: UserProfile?
    ): List<DailyMissingHistory> {
        val histories = mutableListOf<DailyMissingHistory>()
        val today = LocalDate.now()

        for (dayOffset in 0 until 7) {
            val date = today.minusDays(dayOffset.toLong())
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // Load both users' counts for this day
            val myCount = loadTodayCountWithProfile(coupleId, currentUserId, dateString, currentUserProfile)
            val partnerCount = loadTodayCountWithProfile(coupleId, partnerId, dateString, partnerProfile)

            // Only add day if at least one person has > 0 hearts (skip days where both are 0)
            if (myCount.todayCount > 0 || partnerCount.todayCount > 0) {
                val summaries = listOf(
                    DailyMissingSummary(
                        date = date,
                        userId = myCount.userId,
                        userName = myCount.userName,
                        userAvatar = myCount.userAvatar,
                        missCount = myCount.todayCount
                    ),
                    DailyMissingSummary(
                        date = date,
                        userId = partnerCount.userId,
                        userName = partnerCount.userName,
                        userAvatar = partnerCount.userAvatar,
                        missCount = partnerCount.todayCount
                    )
                )

                histories.add(DailyMissingHistory(date = date, summaries = summaries))
            }
        }

        return histories
    }

    /**
     * Load last 7 days history
     */
    private suspend fun loadLast7DaysHistory(
        coupleId: String,
        currentUserId: String,
        partnerId: String
    ): List<DailyMissingHistory> {
        val histories = mutableListOf<DailyMissingHistory>()
        val today = LocalDate.now()

        for (dayOffset in 0 until 7) {
            val date = today.minusDays(dayOffset.toLong())
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // Load both users' counts for this day
            val myCount = loadTodayCount(coupleId, currentUserId, dateString)
            val partnerCount = loadTodayCount(coupleId, partnerId, dateString)

            // Only add day if at least one person has > 0 hearts (skip days where both are 0)
            if (myCount.todayCount > 0 || partnerCount.todayCount > 0) {
                val summaries = listOf(
                    DailyMissingSummary(
                        date = date,
                        userId = myCount.userId,
                        userName = myCount.userName,
                        userAvatar = myCount.userAvatar,
                        missCount = myCount.todayCount
                    ),
                    DailyMissingSummary(
                        date = date,
                        userId = partnerCount.userId,
                        userName = partnerCount.userName,
                        userAvatar = partnerCount.userAvatar,
                        missCount = partnerCount.todayCount
                    )
                )

                histories.add(DailyMissingHistory(date = date, summaries = summaries))
            }
        }

        return histories
    }

    /**
     * Calculate current streak - both users must send hearts on consecutive days
     * Returns a pair of (currentStreak, longestStreak)
     */
    private fun calculateStreakWithLongest(
        history: List<DailyMissingHistory>,
        currentUserId: String,
        partnerId: String
    ): Pair<Int, Int> {
        if (history.isEmpty()) return Pair(0, 0)
        
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 0
        var lastDate: LocalDate? = null
        
        // Sort history by date descending (most recent first)
        val sortedHistory = history.sortedByDescending { it.date }
        
        for (day in sortedHistory) {
            val bothSent = day.summaries.all { it.missCount > 0 }
            
            if (bothSent) {
                if (lastDate == null) {
                    // First day with both sending
                    tempStreak = 1
                } else {
                    // Check if consecutive day
                    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(day.date, lastDate)
                    if (daysDiff == 1L) {
                        tempStreak++
                    } else {
                        // Gap in days, save longest if needed and reset
                        longestStreak = maxOf(longestStreak, tempStreak)
                        tempStreak = 1
                    }
                }
                lastDate = day.date
            } else {
                // Day where not both sent - end current streak
                if (tempStreak > 0) {
                    // Only break current streak if this is a more recent day
                    if (currentStreak == 0) {
                        // This gap means the current streak is what we've counted so far
                        currentStreak = tempStreak
                    }
                    longestStreak = maxOf(longestStreak, tempStreak)
                    tempStreak = 0
                    lastDate = null
                }
            }
        }
        
        // Final check
        longestStreak = maxOf(longestStreak, tempStreak)
        if (currentStreak == 0) {
            currentStreak = tempStreak
        }
        
        Log.d(TAG, "[STREAK] Current: $currentStreak, Longest: $longestStreak")
        return Pair(currentStreak, longestStreak)
    }

    /**
     * Calculate current streak - legacy method for backward compatibility
     */
    private fun calculateStreak(
        history: List<DailyMissingHistory>,
        currentUserId: String,
        partnerId: String
    ): Int {
        return calculateStreakWithLongest(history, currentUserId, partnerId).first
    }

    /**
     * Send a missing signal to partner
     * Uses OPTIMISTIC UPDATE with DEBOUNCE for smooth UI:
     * 1. Update UI IMMEDIATELY on every click (no waiting)
     * 2. Batch multiple rapid clicks into single API call (debounce 500ms)
     * 3. On success: sync with server data
     * 4. On error: rollback only the failed batch
     */
    fun sendMissing() {
        // ========== IMMEDIATE UI UPDATE ==========
        // Update UI INSTANTLY - no waiting for anything
        val currentMyCount = _uiState.value.myTodayCount.todayCount
        val newCount = currentMyCount + 1
        val partnerCount = _uiState.value.partnerTodayCount.todayCount
        val currentUser = _uiState.value.currentUser
        val partnerUser = _uiState.value.partnerUser
        val today = LocalDate.now()
        
        // Track pending hearts for API batch
        pendingApiCount++
        
        // Calculate streak status
        val partnerHasSentToday = partnerCount > 0
        val newHasSentToday = partnerHasSentToday // Both sent
        val currentStreak = _uiState.value.summary.currentStreak
        val newStreak = if (!_uiState.value.summary.hasSentToday && newHasSentToday) {
            currentStreak + 1
        } else {
            currentStreak
        }
        
        // ========== UPDATE DAILY HISTORY OPTIMISTICALLY ==========
        val updatedDailyHistory = updateDailyHistoryOptimistically(
            currentHistory = _uiState.value.dailyHistory,
            today = today,
            currentUser = currentUser,
            partnerUser = partnerUser,
            newMyCount = newCount,
            partnerCount = partnerCount
        )
        
        // INSTANT UI update - user sees change immediately
        _uiState.update {
            it.copy(
                isHeartAnimating = true,
                clickCount = it.clickCount + 1,
                pendingHearts = pendingApiCount,
                myTodayCount = it.myTodayCount.copy(todayCount = newCount),
                dailyHistory = updatedDailyHistory, // Update history immediately
                summary = it.summary.copy(
                    myTodayCount = newCount,
                    todayMissCount = newCount + partnerCount,
                    hasSentToday = newHasSentToday,
                    meSentToday = true,
                    partnerSentToday = partnerHasSentToday,
                    currentStreak = if (newHasSentToday) maxOf(newStreak, 1) else it.summary.currentStreak
                )
            )
        }
        
        // Start animation reset timer (short, independent of API)
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            delay(200)
            _uiState.update { it.copy(isHeartAnimating = false) }
        }
        
        // ========== DEBOUNCED API CALL ==========
        // Cancel previous pending API call and schedule new one
        sendJob?.cancel()
        lastApiCallTime = System.currentTimeMillis()
        
        sendJob = viewModelScope.launch {
            // Wait for user to stop clicking
            delay(API_DEBOUNCE_MS)
            
            // User stopped clicking, now send all pending hearts in one batch
            val heartsToSend = pendingApiCount
            if (heartsToSend <= 0) return@launch
            
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser?.id

                if (currentUserId.isEmpty() || partnerId == null) {
                    Log.e(TAG, "Cannot send missing: no partner")
                    rollbackPendingHearts(heartsToSend)
                    return@launch
                }

                val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
                val todayString = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val recordId = "${coupleId}_${currentUserId}_$todayString"

                Log.d(TAG, "Sending $heartsToSend hearts for recordId: $recordId")

                // Load current server count
                val currentRecord = firestoreRepository.getDocument(
                    "missing_records",
                    recordId,
                    FirebaseMissingRecord::class.java
                ).getOrNull()

                val serverCount = currentRecord?.count ?: 0
                val finalCount = serverCount + heartsToSend

                // Update Firestore with batch count
                val record = FirebaseMissingRecord(
                    id = recordId,
                    coupleId = coupleId,
                    userId = currentUserId,
                    date = todayString,
                    count = finalCount
                )

                firestoreRepository.setDocument(
                    "missing_records",
                    recordId,
                    record,
                    merge = true
                )

                // Clear pending count after successful send
                pendingApiCount = 0
                
                _uiState.update {
                    it.copy(
                        pendingHearts = 0,
                        lastSentTime = System.currentTimeMillis(),
                        sendSuccess = true
                    )
                }

                Log.d(TAG, "Batch sent successfully: $heartsToSend hearts, total: $finalCount")
                
                // Update cache with new count
                viewModelScope.launch {
                    try {
                        cacheRepository.updateTodayCount(
                            coupleId = coupleId,
                            userId = currentUserId,
                            userName = _uiState.value.currentUser.name,
                            userAvatarUrl = _uiState.value.currentUser.avatarUrl,
                            newCount = finalCount
                        )
                        Log.d(TAG, "📦 Updated cache with new count: $finalCount")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating cache", e)
                    }
                }
                
                // Update widget
                WidgetManager.onMissingUpdated(CoupleApplication.instance)
                
                // Notify partner (only once per batch)
                com.example.coupleapp.util.SyncTriggerHelper.notifyMissingSent(CoupleApplication.instance)

                // Sync with server to get latest partner data (don't reset our count)
                loadMissingDataSilent()

                delay(200)
                _uiState.update { it.copy(sendSuccess = false) }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending missing batch", e)
                rollbackPendingHearts(heartsToSend)
            }
        }
    }
    
    /**
     * Rollback pending hearts on error
     * Also rollbacks the daily history to previous state
     */
    private fun rollbackPendingHearts(count: Int) {
        pendingApiCount = maxOf(0, pendingApiCount - count)
        val rolledBackCount = maxOf(0, _uiState.value.myTodayCount.todayCount - count)
        val today = LocalDate.now()
        val currentUser = _uiState.value.currentUser
        val partnerUser = _uiState.value.partnerUser
        val partnerCount = _uiState.value.partnerTodayCount.todayCount
        
        // Rollback history as well
        val rolledBackHistory = updateDailyHistoryOptimistically(
            currentHistory = _uiState.value.dailyHistory,
            today = today,
            currentUser = currentUser,
            partnerUser = partnerUser,
            newMyCount = rolledBackCount,
            partnerCount = partnerCount
        )
        
        _uiState.update {
            it.copy(
                pendingHearts = pendingApiCount,
                myTodayCount = it.myTodayCount.copy(todayCount = rolledBackCount),
                dailyHistory = rolledBackHistory,
                summary = it.summary.copy(myTodayCount = rolledBackCount),
                error = "Không thể gửi. Vui lòng thử lại."
            )
        }
    }
    
    /**
     * Load missing data silently without resetting optimistic counts
     * Used after successful API call to sync partner data
     * Also updates today's entry in history with partner's latest count
     */
    private fun loadMissingDataSilent() {
        viewModelScope.launch {
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser?.id ?: return@launch
                val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
                val todayString = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()
                
                // Only load partner's count to update display
                val partnerTodayResult = loadTodayCount(coupleId, partnerId, todayString)
                
                // Update history with new partner count
                val currentUser = _uiState.value.currentUser
                val partnerUser = _uiState.value.partnerUser
                val myCount = _uiState.value.myTodayCount.todayCount
                
                val updatedHistory = updateDailyHistoryOptimistically(
                    currentHistory = _uiState.value.dailyHistory,
                    today = today,
                    currentUser = currentUser,
                    partnerUser = partnerUser,
                    newMyCount = myCount,
                    partnerCount = partnerTodayResult.todayCount
                )
                
                _uiState.update {
                    it.copy(
                        partnerTodayCount = partnerTodayResult,
                        dailyHistory = updatedHistory,
                        summary = it.summary.copy(
                            partnerTodayCount = partnerTodayResult.todayCount,
                            partnerSentToday = partnerTodayResult.todayCount > 0
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in silent load", e)
            }
        }
    }

    /**
     * Refresh data
     */
    fun refreshData() {
        loadMissingData()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Update daily history optimistically when user sends a heart
     * This ensures "Today" appears in the history immediately
     */
    private fun updateDailyHistoryOptimistically(
        currentHistory: List<DailyMissingHistory>,
        today: LocalDate,
        currentUser: UserProfile,
        partnerUser: UserProfile?,
        newMyCount: Int,
        partnerCount: Int
    ): List<DailyMissingHistory> {
        val mutableHistory = currentHistory.toMutableList()
        
        // Find today's entry in history
        val todayIndex = mutableHistory.indexOfFirst { it.date == today }
        
        // Create updated summaries for today
        val todaySummaries = listOf(
            DailyMissingSummary(
                date = today,
                userId = currentUser.id,
                userName = currentUser.name,
                userAvatar = currentUser.avatarUrl,
                missCount = newMyCount
            ),
            DailyMissingSummary(
                date = today,
                userId = partnerUser?.id ?: "",
                userName = partnerUser?.name ?: "",
                userAvatar = partnerUser?.avatarUrl,
                missCount = partnerCount
            )
        )
        
        val todayEntry = DailyMissingHistory(date = today, summaries = todaySummaries)
        
        if (todayIndex >= 0) {
            // Today already exists in history - update it
            mutableHistory[todayIndex] = todayEntry
        } else {
            // Today doesn't exist - add it at the beginning (most recent first)
            mutableHistory.add(0, todayEntry)
        }
        
        // Keep sorted by date descending and limit to 7 days
        return mutableHistory
            .sortedByDescending { it.date }
            .take(7)
    }

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
        animationJob?.cancel()
        sendJob?.cancel()
        backgroundSyncJob?.cancel()
    }
}

/**
 * UI State for Missing feature with Firebase
 */
data class MissingUiStateFirebase(
    val currentUser: UserProfile = UserProfile("", "", null),
    val partnerUser: UserProfile? = null,
    val dailyHistory: List<DailyMissingHistory> = emptyList(),
    val summary: MissingSummary = MissingSummary(),
    val myTodayCount: UserMissCount = UserMissCount("", "", null, 0),
    val partnerTodayCount: UserMissCount = UserMissCount("", "", null, 0),
    val isLoading: Boolean = true,
    val isHeartAnimating: Boolean = false,
    val clickCount: Int = 0,
    val lastSentTime: Long = 0L,
    val sendSuccess: Boolean = false,
    val error: String? = null,
    // Optimistic update: pending hearts waiting for API confirmation
    val pendingHearts: Int = 0,
    // Last loaded date - to detect day change
    val lastLoadedDate: String = ""
)
