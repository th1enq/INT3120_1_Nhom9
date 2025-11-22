package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.SleepRecord
import com.example.coupleapp.data.model.SleepSettings
import com.example.coupleapp.data.model.UserProfile
import com.example.coupleapp.data.repository.SleepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * ViewModel for Sleep Tracker Screen
 * Manages state and survives configuration changes
 */
class SleepTrackerViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(SleepTrackerUiState())
    val uiState: StateFlow<SleepTrackerUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        val currentUser = SleepRepository.getCurrentUser()
        val partnerUser = SleepRepository.getPartnerUser()
        
        _uiState.update { currentState ->
            currentState.copy(
                currentUser = currentUser,
                partnerUser = partnerUser,
                isCurrentUser = true
            )
        }
        
        loadUserData(currentUser.id)
    }
    
    fun toggleUser() {
        val newIsCurrentUser = !_uiState.value.isCurrentUser
        val userId = if (newIsCurrentUser) {
            _uiState.value.currentUser.id
        } else {
            _uiState.value.partnerUser.id
        }
        
        _uiState.update { it.copy(isCurrentUser = newIsCurrentUser) }
        loadUserData(userId)
    }
    
    private fun loadUserData(userId: String) {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            // Simulate loading delay
            kotlinx.coroutines.delay(300)
            
            val sleepRecord = SleepRepository.getTodaySleepRecord(userId)
            val sleepHistory = SleepRepository.getSleepHistory(userId).take(3)
            val settings = SleepRepository.getSleepSettings(userId)
            
            _uiState.update { currentState ->
                currentState.copy(
                    sleepRecord = sleepRecord,
                    sleepHistory = sleepHistory,
                    settings = settings,
                    isLoading = false
                )
            }
            
            // Check if it's bedtime
            checkBedtimeReminder(settings.idealBedTime)
        }
    }
    
    private fun checkBedtimeReminder(bedTime: LocalTime) {
        val now = LocalTime.now()
        val reminderStart = bedTime.minusMinutes(15) // 15 minutes before bedtime
        val reminderEnd = bedTime.plusMinutes(30) // 30 minutes after bedtime
        
        val shouldShowReminder = now.isAfter(reminderStart) && now.isBefore(reminderEnd)
        
        if (shouldShowReminder) {
            _uiState.update { it.copy(showBedtimeReminder = true) }
        }
    }
    
    fun dismissBedtimeReminder() {
        _uiState.update { it.copy(showBedtimeReminder = false) }
    }
    
    fun updateBedTime(newTime: LocalTime) {
        val currentSettings = _uiState.value.settings
        val updatedSettings = currentSettings.copy(idealBedTime = newTime)
        SleepRepository.updateSleepSettings(currentSettings.userId, updatedSettings)
        
        _uiState.update { it.copy(settings = updatedSettings) }
    }
    
    fun updateSleepGoal(minutes: Int) {
        val currentSettings = _uiState.value.settings
        val updatedSettings = currentSettings.copy(targetSleepDuration = minutes)
        SleepRepository.updateSleepSettings(currentSettings.userId, updatedSettings)
        
        _uiState.update { it.copy(settings = updatedSettings) }
    }
    
    fun showBottomSheet(show: Boolean) {
        _uiState.update { it.copy(showBottomSheet = show) }
    }
    
    fun showTimeEditor(show: Boolean, type: TimeEditorType = TimeEditorType.NONE) {
        _uiState.update { it.copy(showTimeEditor = show, timeEditorType = type) }
    }
    
    fun getActiveUser(): UserProfile {
        return if (_uiState.value.isCurrentUser) {
            _uiState.value.currentUser
        } else {
            _uiState.value.partnerUser
        }
    }
}

/**
 * UI State for Sleep Tracker Screen
 */
data class SleepTrackerUiState(
    val currentUser: UserProfile = UserProfile("", "", null),
    val partnerUser: UserProfile = UserProfile("", "", null),
    val isCurrentUser: Boolean = true,
    val sleepRecord: SleepRecord? = null,
    val sleepHistory: List<SleepRecord> = emptyList(),
    val settings: SleepSettings = SleepSettings(480, LocalTime.of(22, 0), LocalTime.of(6, 0), ""),
    val isLoading: Boolean = true,
    val showBottomSheet: Boolean = false,
    val showTimeEditor: Boolean = false,
    val timeEditorType: TimeEditorType = TimeEditorType.NONE,
    val showBedtimeReminder: Boolean = false
)

enum class TimeEditorType {
    NONE,
    BED_TIME,
    WAKE_UP_TIME,
    SLEEP_GOAL
}
