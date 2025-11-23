package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.SleepRecord
import com.example.coupleapp.data.repository.SleepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

/**
 * ViewModel for Sleep Calendar History Screen
 * Manages calendar state, date selection, and sleep records
 */
class SleepCalendarViewModel(private val userId: String) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SleepCalendarUiState())
    val uiState: StateFlow<SleepCalendarUiState> = _uiState.asStateFlow()
    
    init {
        loadCalendarData()
    }
    
    /**
     * Load calendar data for the user
     */
    private fun loadCalendarData() {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                delay(600)
                
                // Get user and sleep history
                val user = if (userId == SleepRepository.getCurrentUser().id) {
                    SleepRepository.getCurrentUser()
                } else {
                    SleepRepository.getPartnerUser()
                }
                
                val sleepHistory = SleepRepository.getSleepHistory(userId)
                val currentMonth = YearMonth.now()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        userName = user.name,
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
        _uiState.update { it.copy(currentMonth = month, isLoading = true) }
        
        viewModelScope.launch {
            try {
                delay(500)
                
                // Load data for new month (Backend will provide actual data)
                _uiState.update { it.copy(isLoading = false) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Unable to load this month's data."
                    ) 
                }
            }
        }
    }
    
    /**
     * Refresh calendar data
     */
    fun refreshData() {
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                delay(1000)
                
                val sleepHistory = SleepRepository.getSleepHistory(userId)
                
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
    fun getSleepRecordForDate(date: LocalDate): SleepRecord? {
        return _uiState.value.sleepHistory.find { 
            it.date.toLocalDate() == date 
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
    val sleepHistory: List<SleepRecord> = emptyList(),
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val errorMessage: String? = null
)

// Thêm class này vào file ViewModel hoặc file riêng
class SleepCalendarViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SleepCalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SleepCalendarViewModel(userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}