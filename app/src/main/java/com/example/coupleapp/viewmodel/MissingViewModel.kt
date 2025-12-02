package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.MissingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Missing feature
 * Manages UI state and business logic for the missing/longing screen
 */
class MissingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MissingUiState())
    
    val uiState: StateFlow<MissingUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null

    init {
        loadInitialData()
    }

    /**
     * Load initial data including user profiles
     */
    private fun loadInitialData() {
        val currentUser = MissingRepository.getCurrentUser()
        val partnerUser = MissingRepository.getPartnerUser()

        _uiState.update { currentState ->
            currentState.copy(
                currentUser = currentUser,
                partnerUser = partnerUser
            )
        }

        loadData()
    }

    /**
     * Load missing data from repository
     */
    private fun loadData() {
        loadDataJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }

        loadDataJob = viewModelScope.launch {
            try {
                delay(800) // Simulate network delay
                
                val dailyHistory = MissingRepository.getDailyMissingHistory()
                val summary = MissingRepository.getMissingSummary()
                val todayCounts = MissingRepository.getTodayCounts()

                _uiState.update { currentState ->
                    currentState.copy(
                        dailyHistory = dailyHistory,
                        summary = summary,
                        myTodayCount = todayCounts.first,
                        partnerTodayCount = todayCounts.second,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "An error occurred"
                    ) 
                }
            }
        }
    }

    /**
     * Send a missing signal to partner
     * Increments the miss count and triggers heart animation
     */
    private var animationJob: Job? = null
    
    fun sendMissing() {
        // Cancel previous animation job to allow rapid clicking
        animationJob?.cancel()
        
        // Immediately update click count and start animation
        _uiState.update { it.copy(
            isHeartAnimating = true,
            clickCount = it.clickCount + 1
        ) }
        
        animationJob = viewModelScope.launch {
            try {
                // Update data immediately without blocking
                val updatedSummary = MissingRepository.sendMissing()
                val todayCounts = MissingRepository.getTodayCounts()
                val dailyHistory = MissingRepository.getDailyMissingHistory()
                
                _uiState.update { currentState ->
                    currentState.copy(
                        summary = updatedSummary,
                        myTodayCount = todayCounts.first,
                        partnerTodayCount = todayCounts.second,
                        dailyHistory = dailyHistory,
                        lastSentTime = System.currentTimeMillis(),
                        sendSuccess = true
                    )
                }
                
                // Short animation duration
                delay(400)
                _uiState.update { it.copy(isHeartAnimating = false) }
                
                // Reset success flag
                delay(200)
                _uiState.update { it.copy(sendSuccess = false) }
                
            } catch (e: Exception) {
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
        loadData()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Get active user profile
     */
    fun getActiveUser(): UserProfile = _uiState.value.currentUser

    /**
     * Get partner user profile
     */
    fun getPartnerUser(): UserProfile = _uiState.value.partnerUser

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
    }
}

/**
 * UI State for Missing feature
 */
data class MissingUiState(
    val currentUser: UserProfile = UserProfile("", "", null),
    val partnerUser: UserProfile = UserProfile("", "", null),
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
