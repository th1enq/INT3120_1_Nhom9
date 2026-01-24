package com.example.coupleapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseSleepRecord
import com.example.coupleapp.data.repository.ProfileCacheRepository
import com.example.coupleapp.data.repository.SleepCacheRepository
import com.example.coupleapp.data.repository.SleepFirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * ViewModel for Sleep Calendar History Screen
 * Manages calendar state, date selection, and sleep records
 * 
 * Uses Cache-First Strategy:
 * 1. On init: Load cached data immediately (instant UI)
 * 2. Background refresh: Load fresh data from Firebase
 * 3. Cache duration: 30 minutes for history data
 */
class SleepCalendarViewModel(
    private val userId: String,
    context: Context? = null
) : ViewModel() {
    
    private val firebaseRepository = SleepFirebaseRepository(context)
    private val sleepCache = context?.let { SleepCacheRepository.getInstance(it) }
    private val profileCache = context?.let { ProfileCacheRepository.getInstance(it) }
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "SleepCalendarVM"
    }
    
    private val _uiState = MutableStateFlow(SleepCalendarUiState())
    val uiState: StateFlow<SleepCalendarUiState> = _uiState.asStateFlow()
    
    init {
        loadCalendarDataWithCache()
    }
    
    /**
     * Load calendar data with cache-first strategy
     */
    private fun loadCalendarDataWithCache() {
        viewModelScope.launch {
            try {
                // Get user name first
                val userName = getUserName(userId)
                _uiState.update { it.copy(userName = userName) }
                
                // Try to load from cache first
                val cachedHistory = sleepCache?.getCachedHistory(userId)
                val isCacheFresh = sleepCache?.isHistoryCacheFresh(userId) == true
                
                if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                    Log.d(TAG, "📦 Cache found! ${cachedHistory.size} records")
                    
                    // Deduplicate cached history
                    val deduplicatedHistory = deduplicateHistory(cachedHistory)
                    
                    // Show cached data immediately
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            sleepHistory = deduplicatedHistory,
                            currentMonth = YearMonth.now()
                        ) 
                    }
                    
                    // Refresh in background if cache is stale
                    if (!isCacheFresh) {
                        Log.d(TAG, "🔄 Cache is stale, refreshing in background...")
                        loadAndCacheFromFirebase(showLoading = false)
                    } else {
                        Log.d(TAG, "✅ Cache is fresh, no refresh needed")
                    }
                } else {
                    Log.d(TAG, "🌐 No cache, loading from Firebase...")
                    _uiState.update { it.copy(isLoading = true) }
                    loadAndCacheFromFirebase(showLoading = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadCalendarDataWithCache", e)
                _uiState.update { it.copy(isLoading = true) }
                loadCalendarData()
            }
        }
    }
    
    /**
     * Load from Firebase and update cache
     */
    private suspend fun loadAndCacheFromFirebase(showLoading: Boolean) {
        try {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }
            
            val historyResult = firebaseRepository.getSleepHistory(userId, days = 90)
            val rawSleepHistory = historyResult.getOrElse { emptyList() }
            
            // Cache the history
            if (rawSleepHistory.isNotEmpty()) {
                sleepCache?.cacheHistory(userId, rawSleepHistory)
                Log.d(TAG, "📦 Cached ${rawSleepHistory.size} records for user: $userId")
            }
            
            // Deduplicate
            val sleepHistory = deduplicateHistory(rawSleepHistory)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    sleepHistory = sleepHistory,
                    currentMonth = YearMonth.now(),
                    errorMessage = null
                ) 
            }
            Log.d(TAG, "✅ Loaded and cached ${sleepHistory.size} records from Firebase")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from Firebase", e)
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    errorMessage = "Unable to load history. Please try again."
                ) 
            }
        }
    }
    
    /**
     * Deduplicate history by date
     */
    private fun deduplicateHistory(history: List<FirebaseSleepRecord>): List<FirebaseSleepRecord> {
        return history
            .groupBy { record ->
                record.date?.let { timestamp ->
                    timestamp.toDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
            }
            .mapValues { (_, records) ->
                records.maxByOrNull { it.createdAt ?: com.google.firebase.Timestamp.now() }
            }
            .values
            .filterNotNull()
    }
    
    /**
     * Load calendar data from Firebase (fallback)
     */
    private fun loadCalendarData() {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Get user name from Firebase
                val userName = getUserName(userId)
                
                // Get sleep history from Firebase (last 90 days for calendar view)
                val historyResult = firebaseRepository.getSleepHistory(userId, days = 90)
                val rawSleepHistory = historyResult.getOrElse { emptyList() }
                
                // Deduplicate by date
                val sleepHistory = deduplicateHistory(rawSleepHistory)
                
                val currentMonth = YearMonth.now()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        userName = userName,
                        sleepHistory = sleepHistory,
                        currentMonth = currentMonth
                    ) 
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Unable to load history. Please try again."
                    ) 
                }
            }
        }
    }
    
    /**
     * Get user display name - uses ProfileCacheRepository first for instant display
     */
    private suspend fun getUserName(userId: String): String {
        return try {
            val currentUserId = auth.currentUser?.uid
            
            // ========== TRY PROFILE CACHE FIRST (instant, no network) ==========
            if (userId == currentUserId) {
                // Current user - try cache first
                val cachedUser = profileCache?.getCachedCurrentUser()
                if (cachedUser != null && cachedUser.id == userId && cachedUser.displayName.isNotEmpty()) {
                    Log.d(TAG, "📦 Using cached current user name: ${cachedUser.displayName}")
                    return cachedUser.displayName
                }
                
                // Fallback to Firebase Auth or Firestore
                auth.currentUser?.displayName?.takeIf { it.isNotEmpty() }
                    ?: firestore.collection("users").document(userId).get().await()
                        .getString("displayName")
                    ?: "You"
            } else {
                // Partner - try cache first
                val cachedPartner = profileCache?.getCachedPartner()
                if (cachedPartner != null && cachedPartner.id == userId && cachedPartner.displayName.isNotEmpty()) {
                    Log.d(TAG, "📦 Using cached partner name: ${cachedPartner.displayName}")
                    return cachedPartner.displayName
                }
                
                // Fallback to Firestore
                firestore.collection("users").document(userId).get().await()
                    .getString("displayName") ?: "Partner"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            "User"
        }
    }
    
    /**
     * Select a date on the calendar
     */
    fun selectDate(date: LocalDate) {
        val currentSelected = _uiState.value.selectedDate
        _uiState.update { 
            it.copy(selectedDate = if (currentSelected == date) null else date) 
        }
    }
    
    /**
     * Change month
     */
    fun changeMonth(month: YearMonth) {
        _uiState.update { it.copy(currentMonth = month) }
        // Data already loaded for 90 days, no need to reload
    }
    
    /**
     * Refresh calendar data from Firebase (force refresh)
     */
    fun refreshData() {
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                // Invalidate cache first
                sleepCache?.invalidateCache(userId)
                
                // Reload sleep history from Firebase
                val historyResult = firebaseRepository.getSleepHistory(userId, days = 90)
                val rawSleepHistory = historyResult.getOrElse { emptyList() }
                
                // Cache the new data
                if (rawSleepHistory.isNotEmpty()) {
                    sleepCache?.cacheHistory(userId, rawSleepHistory)
                }
                
                val sleepHistory = deduplicateHistory(rawSleepHistory)
                
                _uiState.update { 
                    it.copy(
                        isRefreshing = false,
                        sleepHistory = sleepHistory,
                        errorMessage = null
                    ) 
                }
                Log.d(TAG, "✅ Refreshed and cached ${sleepHistory.size} records")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data", e)
                _uiState.update { 
                    it.copy(
                        isRefreshing = false, 
                        errorMessage = "Unable to refresh data."
                    ) 
                }
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Get sleep record for a specific date
     */
    fun getSleepRecordForDate(date: LocalDate): FirebaseSleepRecord? {
        return _uiState.value.sleepHistory.find { record ->
            record.date?.let { timestamp ->
                val recordDate = timestamp.toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                recordDate == date
            } ?: false
        }
    }
}

/**
 * UI State for Sleep Calendar History Screen
 */
data class SleepCalendarUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val userName: String = "",
    val sleepHistory: List<FirebaseSleepRecord> = emptyList(),
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val errorMessage: String? = null
)

/**
 * Factory for creating SleepCalendarViewModel with dependencies
 */
class SleepCalendarViewModelFactory(
    private val userId: String,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SleepCalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SleepCalendarViewModel(userId, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}