package com.example.coupleapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseSleepRecord
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
 * Uses SleepFirebaseRepository to fetch real data from Firestore
 */
class SleepCalendarViewModel(
    private val userId: String,
    context: Context? = null
) : ViewModel() {
    
    private val firebaseRepository = SleepFirebaseRepository(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _uiState = MutableStateFlow(SleepCalendarUiState())
    val uiState: StateFlow<SleepCalendarUiState> = _uiState.asStateFlow()
    
    init {
        loadCalendarData()
    }
    
    /**
     * Load calendar data from Firebase
     */
    private fun loadCalendarData() {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Get user name from Firebase
                val userName = getUserName(userId)
                
                // Get sleep history from Firebase (last 90 days for calendar view)
                val historyResult = firebaseRepository.getSleepHistory(userId, days = 90)
                val sleepHistory = historyResult.getOrElse { emptyList() }
                
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
     * Get user display name from Firebase
     */
    private suspend fun getUserName(userId: String): String {
        return try {
            val currentUserId = auth.currentUser?.uid
            if (userId == currentUserId) {
                // Current user - get from Firebase Auth or Firestore
                auth.currentUser?.displayName?.takeIf { it.isNotEmpty() }
                    ?: firestore.collection("users").document(userId).get().await()
                        .getString("displayName")
                    ?: "You"
            } else {
                // Partner - get from Firestore
                firestore.collection("users").document(userId).get().await()
                    .getString("displayName") ?: "Partner"
            }
        } catch (e: Exception) {
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
     * Refresh calendar data from Firebase
     */
    fun refreshData() {
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                // Reload sleep history from Firebase
                val historyResult = firebaseRepository.getSleepHistory(userId, days = 90)
                val sleepHistory = historyResult.getOrElse { emptyList() }
                
                _uiState.update { 
                    it.copy(
                        isRefreshing = false,
                        sleepHistory = sleepHistory,
                        errorMessage = null
                    ) 
                }
                
            } catch (e: Exception) {
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