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
class SleepTrackerViewModelFirebase(
    private val context: Context? = null
) : ViewModel() {
    private val sleepRepository = SleepFirebaseRepository(context)
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
        checkAndAutoSync()
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
                
                // Check and perform auto-sync if needed
                tryAutoSync(userId)

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
                var sleepRecord = firebaseRecord?.let { sleepRepository.convertToSleepRecord(it) }
                Log.d(TAG, "loadUserData: Today's record = ${sleepRecord?.id}")

                // Load sleep history
                val historyResult = sleepRepository.getSleepHistory(userId, 7)
                val firebaseHistory = historyResult.getOrElse { emptyList() }
                val sleepHistory = firebaseHistory.map { sleepRepository.convertToSleepRecord(it) }
                
                Log.d(TAG, "loadUserData: Found ${sleepHistory.size} history records")
                
                // If no today's record, use most recent record from history for display
                if (sleepRecord == null && sleepHistory.isNotEmpty()) {
                    sleepRecord = sleepHistory.first()
                    Log.d(TAG, "loadUserData: Using most recent record from history: ${sleepRecord.id}")
                }

                _uiState.update { currentState ->
                    currentState.copy(
                        sleepRecord = sleepRecord,
                        sleepHistory = sleepHistory.take(3),
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
    
    /**
     * Insert mock sleep data for testing (includes current user and partner)
     */
    fun insertMockSleepData() {
        viewModelScope.launch {
            try {
                val currentUserId = _uiState.value.currentUser.id
                val partnerId = _uiState.value.partnerUser.id.takeIf { it.isNotEmpty() }
                
                Log.d(TAG, "insertMockSleepData: Current user=$currentUserId, Partner=$partnerId")
                
                _uiState.update { it.copy(
                    isLoading = true,
                    healthConnectSyncStatus = "Inserting mock data..."
                ) }
                
                val result = sleepRepository.insertMockSleepData(currentUserId, partnerId)
                
                if (result.isSuccess) {
                    Log.d(TAG, "insertMockSleepData: Success! Reloading data...")
                    
                    val partnerText = if (partnerId != null) " and partner" else ""
                    _uiState.update { it.copy(
                        healthConnectSyncStatus = "Mock data inserted for you${partnerText}! (7 days)"
                    ) }
                    
                    // Reload data after insertion
                    loadUserData(getActiveUser().id, false)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "insertMockSleepData: Failed - $error")
                    _uiState.update { it.copy(
                        isLoading = false,
                        healthConnectSyncStatus = "Failed to insert mock data: $error"
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "insertMockSleepData: Error", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    healthConnectSyncStatus = "Error: ${e.message}"
                ) }
            }
        }
    }
    
    /**
     * Check if Health Connect is available
     */
    suspend fun checkHealthConnectAvailability(): Boolean {
        return try {
            sleepRepository.isHealthConnectAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect availability", e)
            false
        }
    }
    
    /**
     * Get Health Connect permission contract for activity result launcher
     */
    fun getHealthConnectPermissionContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> {
        return sleepRepository.getHealthConnectPermissionContract()
    }
    
    /**
     * Get required Health Connect permissions
     */
    fun getRequiredHealthConnectPermissions(): Set<String> {
        return sleepRepository.getRequiredHealthConnectPermissions()
    }
    
    /**
     * Check if Health Connect permissions are granted
     */
    suspend fun hasHealthConnectPermissions(): Boolean {
        return try {
            sleepRepository.hasHealthConnectPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect permissions", e)
            false
        }
    }
    
    /**
     * Sync sleep data from Health Connect to Firebase
     */
    fun syncFromHealthConnect() {
        viewModelScope.launch {
            try {
                val userId = getActiveUser().id
                Log.d(TAG, "syncFromHealthConnect: Starting sync for user=$userId")
                
                _uiState.update { it.copy(isLoading = true, healthConnectSyncStatus = "Syncing...") }
                
                val result = sleepRepository.syncSleepDataFromHealthConnect(userId)
                
                if (result.isSuccess) {
                    val syncCount = result.getOrNull() ?: 0
                    Log.d(TAG, "syncFromHealthConnect: Successfully synced $syncCount records")
                    _uiState.update { it.copy(healthConnectSyncStatus = "Synced $syncCount records") }
                    
                    // Reload data after sync
                    loadUserData(userId, false)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "syncFromHealthConnect: Failed - $error")
                    _uiState.update { it.copy(
                        isLoading = false,
                        healthConnectSyncStatus = "Sync failed: $error"
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncFromHealthConnect: Error", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    healthConnectSyncStatus = "Sync error: ${e.message}"
                ) }
            }
        }
    }
    
    /**
     * Clear Health Connect sync status message
     */
    fun clearSyncStatus() {
        _uiState.update { it.copy(healthConnectSyncStatus = null) }
    }
    
    /**
     * Check and perform auto-sync daily
     */
    private fun checkAndAutoSync() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            tryAutoSync(currentUser.uid)
        }
    }

    /**
     * Try to auto-sync yesterday's sleep data if needed
     */
    private suspend fun tryAutoSync(userId: String) {
        try {
            val ctx = context ?: run {
                Log.w(TAG, "tryAutoSync: Context is null")
                return
            }
            
            val shouldSync = sleepRepository.shouldAutoSync(userId)
            
            if (shouldSync) {
                Log.d(TAG, "tryAutoSync: Attempting auto-sync for yesterday's data")
                
                val healthManager = com.example.coupleapp.data.health.HealthConnectManager(ctx)
                val result = sleepRepository.autoSyncYesterdaySleepData(userId, healthManager)
                
                if (result.isSuccess && result.getOrNull() == true) {
                    sleepRepository.updateLastAutoSyncTime(userId)
                    Log.d(TAG, "tryAutoSync: Auto-sync completed successfully")
                    
                    // Refresh UI to show new data
                    loadUserData(userId, false)
                } else {
                    Log.d(TAG, "tryAutoSync: No data to sync or sync skipped")
                }
            } else {
                Log.d(TAG, "tryAutoSync: Auto-sync not needed (already synced today)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryAutoSync: Error during auto-sync", e)
        }
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
    val showWidgetInstructions: Boolean = false,
    val healthConnectSyncStatus: String? = null
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
