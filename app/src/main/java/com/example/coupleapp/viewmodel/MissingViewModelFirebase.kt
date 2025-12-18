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

                Log.d(TAG, "Loading missing data for coupleId: $coupleId")

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
                
                // Check if both users sent today
                val hasSentToday = myTodayResult.todayCount > 0 && partnerTodayResult.todayCount > 0

                val summary = MissingSummary(
                    totalMissCount = totalMissing,
                    todayMissCount = myTodayResult.todayCount + partnerTodayResult.todayCount,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    hasSentToday = hasSentToday,
                    myTodayCount = myTodayResult.todayCount,
                    partnerTodayCount = partnerTodayResult.todayCount
                )

                _uiState.update {
                    it.copy(
                        dailyHistory = historyList,
                        summary = summary,
                        myTodayCount = myTodayResult,
                        partnerTodayCount = partnerTodayResult,
                        isLoading = false
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
     */
    fun sendMissing() {
        animationJob?.cancel()

        _uiState.update {
            it.copy(
                isHeartAnimating = true,
                clickCount = it.clickCount + 1
            )
        }

        animationJob = viewModelScope.launch {
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser?.id

                if (currentUserId.isEmpty() || partnerId == null) {
                    Log.e(TAG, "Cannot send missing: no partner")
                    _uiState.update { it.copy(isHeartAnimating = false) }
                    return@launch
                }

                val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val recordId = "${coupleId}_${currentUserId}_$today"

                Log.d(TAG, "Sending missing for recordId: $recordId")

                // Load current count
                val currentRecord = firestoreRepository.getDocument(
                    "missing_records",
                    recordId,
                    FirebaseMissingRecord::class.java
                ).getOrNull()

                val newCount = (currentRecord?.count ?: 0) + 1

                // Update or create record
                val record = FirebaseMissingRecord(
                    id = recordId,
                    coupleId = coupleId,
                    userId = currentUserId,
                    date = today,
                    count = newCount
                )

                firestoreRepository.setDocument(
                    "missing_records",
                    recordId,
                    record,
                    merge = true
                )

                // Update UI immediately
                val myCount = _uiState.value.myTodayCount.copy(todayCount = newCount)
                val partnerCount = _uiState.value.partnerTodayCount
                
                // Update hasSentToday in summary
                val hasSentToday = newCount > 0 && partnerCount.todayCount > 0
                val updatedSummary = _uiState.value.summary.copy(
                    hasSentToday = hasSentToday,
                    myTodayCount = newCount,
                    todayMissCount = newCount + partnerCount.todayCount
                )
                
                _uiState.update {
                    it.copy(
                        myTodayCount = myCount,
                        summary = updatedSummary,
                        lastSentTime = System.currentTimeMillis(),
                        sendSuccess = true
                    )
                }

                Log.d(TAG, "Missing sent successfully, new count: $newCount, hasSentToday: $hasSentToday")
                
                // Update widget immediately after sending missing
                WidgetManager.onMissingUpdated(CoupleApplication.instance)

                // Reload data to update history and streak
                loadMissingData()

                // Animation
                delay(400)
                _uiState.update { it.copy(isHeartAnimating = false) }

                delay(200)
                _uiState.update { it.copy(sendSuccess = false) }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending missing", e)
                _uiState.update {
                    it.copy(
                        isHeartAnimating = false,
                        error = e.message
                    )
                }
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
    val error: String? = null
)
