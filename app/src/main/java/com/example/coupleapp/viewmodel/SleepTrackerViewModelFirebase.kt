package com.example.coupleapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.SleepFirebaseRepository
import com.example.coupleapp.widget.SleepWidgetManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Sleep Tracker ViewModel with Firebase integration
 */
class SleepTrackerViewModelFirebase : ViewModel() {
    private val sleepRepository = SleepFirebaseRepository()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "SleepTrackerViewModel"
    }

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
        Log.d(TAG, "SleepTrackerViewModelFirebase initialized")
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val userId = currentUser.uid
                Log.d(TAG, "Loading data for user: $userId")

                // Load current user profile
                val currentUserResult = sleepRepository.getUserProfile(userId)
                val currentUserProfile = currentUserResult.getOrElse {
                    UserProfile(userId, "User", null)
                }

                // Load partner profile
                val partnerIdResult = sleepRepository.getPartnerId()
                val partnerId = partnerIdResult.getOrNull()
                
                var partnerProfile = UserProfile("", "Partner", null)
                if (partnerId != null) {
                    val partnerResult = sleepRepository.getUserProfile(partnerId)
                    partnerProfile = partnerResult.getOrElse {
                        UserProfile(partnerId, "Partner", null)
                    }
                }

                _uiState.update { currentState ->
                    currentState.copy(
                        currentUser = currentUserProfile,
                        partnerUser = partnerProfile,
                        isCurrentUser = true
                    )
                }

                // Load current user's sleep data
                loadUserData(userId, isInitialLoad = true)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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

                Log.d(TAG, "Loading sleep data for user: $userId")

                // Load settings
                val settingsResult = sleepRepository.getSleepSettings(userId)
                val firebaseSettings = settingsResult.getOrNull()
                Log.d(TAG, "loadUserData: Firebase settings = $firebaseSettings")
                
                val settings = firebaseSettings?.let { sleepRepository.convertToSleepSettings(it) }
                    ?: SleepSettings(
                        targetSleepDuration = 480,
                        idealBedTime = LocalTime.of(22, 0),
                        idealWakeUpTime = LocalTime.of(6, 0),
                        userId = userId
                    )
                Log.d(TAG, "loadUserData: Converted settings - bedTime=${settings.idealBedTime}, wakeTime=${settings.idealWakeUpTime}, duration=${settings.targetSleepDuration}")

                // Load today's sleep record
                val todayRecordResult = sleepRepository.getTodaySleepRecord(userId)
                val firebaseRecord = todayRecordResult.getOrNull()
                val sleepRecord = firebaseRecord?.let { sleepRepository.convertToSleepRecord(it) }

                // Load sleep history
                val historyResult = sleepRepository.getSleepHistory(userId, 7)
                val firebaseHistory = historyResult.getOrElse { emptyList() }
                val sleepHistory = firebaseHistory.map { sleepRepository.convertToSleepRecord(it) }
                    .take(3)

                _uiState.update { currentState ->
                    currentState.copy(
                        sleepRecord = sleepRecord,
                        sleepHistory = sleepHistory,
                        settings = settings,
                        isLoading = false
                    )
                }

                // Check bedtime reminder
                checkBedtimeReminder(settings.idealBedTime)

                Log.d(TAG, "Sleep data loaded successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading sleep data", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun checkBedtimeReminder(bedTime: LocalTime) {
        val (isTimeToSleep, message) = sleepRepository.checkTimeToSleep(bedTime)
        
        if (isTimeToSleep) {
            _uiState.update { it.copy(showBedtimeReminder = true) }
        }
    }

    /**
     * Update when to sleep (bedtime)
     */
    fun updateBedTime(newBedTime: LocalTime) {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                val currentSettings = _uiState.value.settings ?: return@launch
                
                Log.d(TAG, "updateBedTime: Old bedtime = ${currentSettings.idealBedTime}, New bedtime = $newBedTime")
                
                val firebaseSettings = FirebaseSleepSettings(
                    id = userId,
                    userId = userId,
                    targetSleepDurationMinutes = currentSettings.targetSleepDuration,
                    idealBedTimeHour = newBedTime.hour,
                    idealBedTimeMinute = newBedTime.minute,
                    idealWakeUpTimeHour = currentSettings.idealWakeUpTime.hour,
                    idealWakeUpTimeMinute = currentSettings.idealWakeUpTime.minute
                )
                
                val result = sleepRepository.updateSleepSettings(firebaseSettings)
                if (result.isSuccess) {
                    Log.d(TAG, "Bedtime updated successfully in Firebase")
                    // Update local state immediately
                    _uiState.update { currentState ->
                        currentState.copy(
                            settings = currentSettings.copy(idealBedTime = newBedTime)
                        )
                    }
                    Log.d(TAG, "Local state updated: idealBedTime = ${_uiState.value.settings?.idealBedTime}")
                    checkBedtimeReminder(newBedTime)
                } else {
                    Log.e(TAG, "Failed to update bedtime: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating bedtime", e)
            }
        }
    }

    /**
     * Update wake up time
     */
    fun updateWakeUpTime(newWakeUpTime: LocalTime) {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                val currentSettings = _uiState.value.settings ?: return@launch
                
                val firebaseSettings = FirebaseSleepSettings(
                    id = userId,
                    userId = userId,
                    targetSleepDurationMinutes = currentSettings.targetSleepDuration,
                    idealBedTimeHour = currentSettings.idealBedTime.hour,
                    idealBedTimeMinute = currentSettings.idealBedTime.minute,
                    idealWakeUpTimeHour = newWakeUpTime.hour,
                    idealWakeUpTimeMinute = newWakeUpTime.minute
                )
                
                val result = sleepRepository.updateSleepSettings(firebaseSettings)
                if (result.isSuccess) {
                    Log.d(TAG, "Wake up time updated successfully")
                    // Update local state immediately
                    _uiState.update { currentState ->
                        currentState.copy(
                            settings = currentSettings.copy(idealWakeUpTime = newWakeUpTime)
                        )
                    }
                } else {
                    Log.e(TAG, "Failed to update wake up time: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating wake up time", e)
            }
        }
    }

    /**
     * Update sleep goal (target duration)
     */
    fun updateSleepGoal(durationMinutes: Int) {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                val currentSettings = _uiState.value.settings ?: return@launch
                
                Log.d(TAG, "updateSleepGoal: Old duration = ${currentSettings.targetSleepDuration} mins, New duration = $durationMinutes mins")
                
                val firebaseSettings = FirebaseSleepSettings(
                    id = userId,
                    userId = userId,
                    targetSleepDurationMinutes = durationMinutes,
                    idealBedTimeHour = currentSettings.idealBedTime.hour,
                    idealBedTimeMinute = currentSettings.idealBedTime.minute,
                    idealWakeUpTimeHour = currentSettings.idealWakeUpTime.hour,
                    idealWakeUpTimeMinute = currentSettings.idealWakeUpTime.minute
                )
                
                val result = sleepRepository.updateSleepSettings(firebaseSettings)
                if (result.isSuccess) {
                    Log.d(TAG, "Sleep goal updated successfully in Firebase")
                    // Update local state immediately
                    _uiState.update { currentState ->
                        currentState.copy(
                            settings = currentSettings.copy(targetSleepDuration = durationMinutes)
                        )
                    }
                    Log.d(TAG, "Local state updated: targetSleepDuration = ${_uiState.value.settings?.targetSleepDuration} mins")
                } else {
                    Log.e(TAG, "Failed to update sleep goal: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sleep goal", e)
            }
        }
    }

    /**
     * Start sleep tracking
     */
    fun startSleepTracking() {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                val currentSettings = _uiState.value.settings ?: return@launch
                val now = LocalTime.now()
                
                val firebaseRecord = FirebaseSleepRecord(
                    userId = userId,
                    coupleId = "", // Will be filled by repository if needed
                    date = Timestamp(Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())),
                    bedTimeHour = now.hour,
                    bedTimeMinute = now.minute,
                    wakeUpTimeHour = 0,
                    wakeUpTimeMinute = 0,
                    actualSleepDurationMinutes = 0,
                    targetSleepDurationMinutes = currentSettings.targetSleepDuration,
                    awakeDurationMinutes = 0,
                    sleepDurationMinutes = 0,
                    quality = "GOOD",
                    achievementPercentage = 0f
                )
                
                val result = sleepRepository.saveSleepRecord(firebaseRecord)
                if (result.isSuccess) {
                    Log.d(TAG, "Sleep tracking started")
                    loadUserData(userId, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting sleep tracking", e)
            }
        }
    }

    /**
     * End sleep tracking
     */
    fun endSleepTracking() {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                val currentRecord = _uiState.value.sleepRecord ?: return@launch
                val now = LocalTime.now()
                
                // Calculate duration
                val bedTime = currentRecord.bedTime
                val duration = java.time.Duration.between(bedTime, now).toMinutes().toInt()
                
                // Calculate quality
                val (quality, percentage) = sleepRepository.calculateSleepQuality(
                    duration,
                    currentRecord.targetSleepDuration
                )
                
                val updatedRecord = FirebaseSleepRecord(
                    id = currentRecord.id,
                    userId = userId,
                    coupleId = "",
                    date = Timestamp(Date.from(currentRecord.date.atZone(ZoneId.systemDefault()).toInstant())),
                    bedTimeHour = bedTime.hour,
                    bedTimeMinute = bedTime.minute,
                    wakeUpTimeHour = now.hour,
                    wakeUpTimeMinute = now.minute,
                    actualSleepDurationMinutes = duration,
                    targetSleepDurationMinutes = currentRecord.targetSleepDuration,
                    awakeDurationMinutes = currentRecord.sleepStages.awakeDurationMinutes,
                    sleepDurationMinutes = duration - currentRecord.sleepStages.awakeDurationMinutes,
                    quality = quality.name,
                    achievementPercentage = percentage
                )
                
                val result = sleepRepository.saveSleepRecord(updatedRecord)
                if (result.isSuccess) {
                    Log.d(TAG, "Sleep tracking ended")
                    loadUserData(userId, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ending sleep tracking", e)
            }
        }
    }

    fun getActiveUser(): UserProfile {
        return if (_uiState.value.isCurrentUser) {
            _uiState.value.currentUser
        } else {
            _uiState.value.partnerUser
        }
    }

    fun showBottomSheet(show: Boolean) {
        _uiState.update { it.copy(showBottomSheet = show) }
    }

    fun showTimeEditor(show: Boolean, type: TimeEditorType? = null) {
        _uiState.update { it.copy(
            showTimeEditor = show,
            timeEditorType = type ?: TimeEditorType.NONE
        )}
    }

    fun dismissBedtimeReminder() {
        _uiState.update { it.copy(showBedtimeReminder = false) }
    }

    fun updateWidgets(context: Context) {
        // TODO: Implement widget update if needed
        Log.d(TAG, "Widget update requested")
    }
}

enum class TimeEditorType {
    NONE,
    BED_TIME,
    WAKE_UP_TIME,
    SLEEP_GOAL
}

data class SleepTrackerUiState(
    val isLoading: Boolean = false,
    val currentUser: UserProfile = UserProfile("", "", null),
    val partnerUser: UserProfile = UserProfile("", "", null),
    val isCurrentUser: Boolean = true,
    val sleepRecord: SleepRecord? = null,
    val sleepHistory: List<SleepRecord> = emptyList(),
    val settings: SleepSettings? = null,
    val showBottomSheet: Boolean = false,
    val showTimeEditor: Boolean = false,
    val timeEditorType: TimeEditorType = TimeEditorType.NONE,
    val showBedtimeReminder: Boolean = false,
    val showWidgetInstructions: Boolean = false
) {
    val isContentReady: Boolean
        get() = !isLoading && sleepRecord != null
}

fun SleepTrackerUiState.getActiveUserProfile(): UserProfile {
    return if (isCurrentUser) currentUser else partnerUser
}

fun SleepTrackerUiState.hasData(): Boolean {
    return sleepRecord != null && sleepHistory.isNotEmpty()
}
