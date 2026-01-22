package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.widget.WidgetManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel for Missing feature with Firebase integration
 */
class MissingViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    companion object {
        private const val TAG = "MissingViewModel"
    }

    private val _uiState = MutableStateFlow(MissingUiStateFirebase())
    val uiState: StateFlow<MissingUiStateFirebase> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null
    private var animationJob: Job? = null
    private var sendJob: Job? = null
    
    // Debounce mechanism for rapid clicks
    private var pendingApiCount = 0
    private var lastApiCallTime = 0L
    private val API_DEBOUNCE_MS = 500L // Wait 500ms after last click before calling API

    init {
        Log.d(TAG, "MissingViewModelFirebase initialized")
        loadInitialData()
    }

    /**
     * Load initial data including user profiles
     */
    private fun loadInitialData() {
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
                }

                _uiState.update {
                    it.copy(
                        currentUser = currentUserProfile,
                        partnerUser = partnerProfile,
                        isLoading = false
                    )
                }

                // Load missing data
                loadMissingData()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
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
        
        // INSTANT UI update - user sees change immediately
        _uiState.update {
            it.copy(
                isHeartAnimating = true,
                clickCount = it.clickCount + 1,
                pendingHearts = pendingApiCount,
                myTodayCount = it.myTodayCount.copy(todayCount = newCount),
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
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val recordId = "${coupleId}_${currentUserId}_$today"

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
                    date = today,
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
     */
    private fun rollbackPendingHearts(count: Int) {
        pendingApiCount = maxOf(0, pendingApiCount - count)
        val rolledBackCount = maxOf(0, _uiState.value.myTodayCount.todayCount - count)
        _uiState.update {
            it.copy(
                pendingHearts = pendingApiCount,
                myTodayCount = it.myTodayCount.copy(todayCount = rolledBackCount),
                summary = it.summary.copy(myTodayCount = rolledBackCount),
                error = "Không thể gửi. Vui lòng thử lại."
            )
        }
    }
    
    /**
     * Load missing data silently without resetting optimistic counts
     * Used after successful API call to sync partner data
     */
    private fun loadMissingDataSilent() {
        viewModelScope.launch {
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser?.id ?: return@launch
                val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Only load partner's count to update display
                val partnerTodayResult = loadTodayCount(coupleId, partnerId, today)
                
                _uiState.update {
                    it.copy(
                        partnerTodayCount = partnerTodayResult,
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

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
        animationJob?.cancel()
        sendJob?.cancel()
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
