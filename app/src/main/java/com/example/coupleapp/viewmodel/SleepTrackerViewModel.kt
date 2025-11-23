package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.SleepRecord
import com.example.coupleapp.data.model.SleepSettings
import com.example.coupleapp.data.model.UserProfile
import com.example.coupleapp.data.repository.SleepRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalTime


class SleepTrackerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SleepTrackerUiState())

    val uiState: StateFlow<SleepTrackerUiState> = _uiState
        .debounce(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SleepTrackerUiState()
        )

    private var loadDataJob: Job? = null

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

        loadUserData(currentUser.id, isInitialLoad = true)
    }

    fun toggleUser() {
        loadDataJob?.cancel()

        val newIsCurrentUser = !_uiState.value.isCurrentUser
        val userId = if (newIsCurrentUser) {
            _uiState.value.currentUser.id
        } else {
            _uiState.value.partnerUser.id
        }

        _uiState.update { it.copy(isCurrentUser = newIsCurrentUser) }
        loadUserData(userId, isInitialLoad = false)
    }

    private fun loadUserData(userId: String, isInitialLoad: Boolean) {
        loadDataJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }

        loadDataJob = viewModelScope.launch {
            try {
                if (isInitialLoad) {
                    kotlinx.coroutines.delay(500)
                } else {
                    kotlinx.coroutines.delay(200)
                }

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
                checkBedtimeReminder(settings.idealBedTime)

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun checkBedtimeReminder(bedTime: LocalTime) {
        val now = LocalTime.now()
        val reminderStart = bedTime.minusMinutes(15)
        val reminderEnd = bedTime.plusMinutes(30)

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

        // Optimistic update - Update UI ngay lập tức
        _uiState.update { it.copy(settings = updatedSettings) }

        // Save to repository in background
        viewModelScope.launch {
            try {
                SleepRepository.updateSleepSettings(currentSettings.userId, updatedSettings)
                loadUserData(getActiveUser().id, isInitialLoad = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(settings = currentSettings) }
            }
        }
    }

    fun updateSleepGoal(minutes: Int) {
        val currentSettings = _uiState.value.settings
        val updatedSettings = currentSettings.copy(targetSleepDuration = minutes)

        _uiState.update { it.copy(settings = updatedSettings) }

        viewModelScope.launch {
            try {
                SleepRepository.updateSleepSettings(currentSettings.userId, updatedSettings)
                loadUserData(getActiveUser().id, isInitialLoad = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(settings = currentSettings) }
            }
        }
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

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
    }
}

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
) {
    val isContentReady: Boolean
        get() = !isLoading && sleepRecord != null
}

enum class TimeEditorType {
    NONE,
    BED_TIME,
    WAKE_UP_TIME,
    SLEEP_GOAL
}


fun SleepTrackerUiState.getActiveUserProfile(): UserProfile {
    return if (isCurrentUser) currentUser else partnerUser
}

fun SleepTrackerUiState.hasData(): Boolean {
    return sleepRecord != null && sleepHistory.isNotEmpty()
}