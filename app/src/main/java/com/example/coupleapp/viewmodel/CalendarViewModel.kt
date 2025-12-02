package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.CalendarRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * ViewModel for Calendar screen with love days counter
 */
class CalendarViewModel : ViewModel() {
    
    private val repository = CalendarRepository()
    
    // UI State
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    
    // Live counter update job
    private var counterUpdateJob: Job? = null
    
    init {
        loadData()
        startLiveCounter()
    }
    
    /**
     * Load initial data
     */
    private fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            // Load couple profile
            repository.coupleProfile.collect { profile ->
                _uiState.update { it.copy(coupleProfile = profile, isLoading = false) }
                updateLoveDaysCounter(profile.relationshipStartDate)
            }
        }
        
        viewModelScope.launch {
            // Load anniversaries
            repository.anniversaries.collect { anniversaries ->
                _uiState.update { it.copy(allAnniversaries = anniversaries) }
                updateUpcomingEvents()
                updateCalendarEvents()
            }
        }
        
        viewModelScope.launch {
            // Load settings
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }
    
    /**
     * Start live counter that updates every second
     */
    private fun startLiveCounter() {
        counterUpdateJob?.cancel()
        counterUpdateJob = viewModelScope.launch {
            while (true) {
                _uiState.value.coupleProfile?.let { profile ->
                    updateLoveDaysCounter(profile.relationshipStartDate)
                }
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Update love days counter
     */
    private fun updateLoveDaysCounter(startDate: LocalDateTime) {
        val counter = repository.calculateLoveDays(startDate)
        _uiState.update { it.copy(loveDaysCounter = counter) }
    }
    
    /**
     * Update upcoming events
     */
    private fun updateUpcomingEvents() {
        val upcomingEvents = repository.getUpcomingAnniversaries()
        _uiState.update { it.copy(upcomingEvents = upcomingEvents) }
    }
    
    /**
     * Update calendar events for current selected month
     */
    private fun updateCalendarEvents() {
        val currentMonth = _uiState.value.selectedYearMonth
        val events = repository.getEventsForMonth(currentMonth.year, currentMonth.monthValue)
        _uiState.update { it.copy(calendarEvents = events) }
    }
    
    /**
     * Toggle view mode between circle counter and grid calendar
     */
    fun toggleViewMode() {
        val currentMode = _uiState.value.viewMode
        val newMode = if (currentMode == CalendarViewMode.CIRCLE_COUNTER) {
            CalendarViewMode.GRID_CALENDAR
        } else {
            CalendarViewMode.CIRCLE_COUNTER
        }
        _uiState.update { it.copy(viewMode = newMode) }
    }
    
    /**
     * Navigate to previous month in calendar
     */
    fun previousMonth() {
        val currentMonth = _uiState.value.selectedYearMonth
        val newMonth = currentMonth.minusMonths(1)
        _uiState.update { it.copy(selectedYearMonth = newMonth) }
        updateCalendarEvents()
    }
    
    /**
     * Navigate to next month in calendar
     */
    fun nextMonth() {
        val currentMonth = _uiState.value.selectedYearMonth
        val newMonth = currentMonth.plusMonths(1)
        _uiState.update { it.copy(selectedYearMonth = newMonth) }
        updateCalendarEvents()
    }
    
    /**
     * Select a specific date
     */
    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        val eventsOnDate = _uiState.value.calendarEvents[date] ?: emptyList()
        if (eventsOnDate.isNotEmpty()) {
            _uiState.update { it.copy(showEventDialog = true) }
        }
    }
    
    /**
     * Show add event dialog
     */
    fun showAddEventDialog(date: LocalDate? = null) {
        _uiState.update {
            it.copy(
                showAddEventDialog = true,
                selectedDate = date ?: LocalDate.now(),
                editingAnniversary = null
            )
        }
    }
    
    /**
     * Show edit event dialog
     */
    fun showEditEventDialog(anniversary: Anniversary) {
        _uiState.update {
            it.copy(
                showAddEventDialog = true,
                editingAnniversary = anniversary,
                selectedDate = anniversary.date.toLocalDate()
            )
        }
    }
    
    /**
     * Hide event dialog
     */
    fun hideEventDialog() {
        _uiState.update {
            it.copy(
                showEventDialog = false,
                showAddEventDialog = false,
                editingAnniversary = null
            )
        }
    }
    
    /**
     * Add or update anniversary
     */
    fun saveAnniversary(anniversary: Anniversary) {
        viewModelScope.launch {
            if (_uiState.value.editingAnniversary != null) {
                repository.updateAnniversary(anniversary)
            } else {
                repository.addAnniversary(anniversary)
            }
            hideEventDialog()
        }
    }
    
    /**
     * Delete anniversary
     */
    fun deleteAnniversary(anniversaryId: String) {
        viewModelScope.launch {
            repository.deleteAnniversary(anniversaryId)
            hideEventDialog()
        }
    }
    
    /**
     * Show settings dialog
     */
    fun showSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }
    
    /**
     * Hide settings dialog
     */
    fun hideSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }
    
    /**
     * Update user nickname
     */
    fun updateNickname(userId: String, newNickname: String) {
        viewModelScope.launch {
            _uiState.value.coupleProfile?.let { profile ->
                val user = if (userId == profile.user1.id) profile.user1 else profile.user2
                val updatedUser = user.copy(nickname = newNickname)
                repository.updateUserProfile(userId, updatedUser)
            }
        }
    }
    
    /**
     * Update background image
     */
    fun updateBackgroundImage(imageUrl: String) {
        viewModelScope.launch {
            repository.updateBackgroundImage(imageUrl)
            val currentSettings = _uiState.value.settings
            repository.updateSettings(
                currentSettings.copy(
                    backgroundImageUrl = imageUrl,
                    useDefaultBackground = imageUrl.isEmpty()
                )
            )
        }
    }
    
    /**
     * Toggle heartbeat animation
     */
    fun toggleHeartbeatAnimation(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = _uiState.value.settings
            repository.updateSettings(
                currentSettings.copy(showHeartbeatAnimation = enabled)
            )
        }
    }
    
    /**
     * Show anniversary management screen
     */
    fun showAnniversaryManagement() {
        _uiState.update { it.copy(showAnniversaryManagement = true) }
    }
    
    /**
     * Hide anniversary management screen
     */
    fun hideAnniversaryManagement() {
        _uiState.update { it.copy(showAnniversaryManagement = false) }
    }
    
    override fun onCleared() {
        super.onCleared()
        counterUpdateJob?.cancel()
    }
}

/**
 * UI State for Calendar Screen
 */
data class CalendarUiState(
    val coupleProfile: CoupleProfile? = null,
    val loveDaysCounter: LoveDaysCounter? = null,
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val allAnniversaries: List<Anniversary> = emptyList(),
    val calendarEvents: Map<LocalDate, List<Anniversary>> = emptyMap(),
    val settings: CalendarSettings = CalendarSettings(),
    val viewMode: CalendarViewMode = CalendarViewMode.CIRCLE_COUNTER,
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val showEventDialog: Boolean = false,
    val showAddEventDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showAnniversaryManagement: Boolean = false,
    val editingAnniversary: Anniversary? = null,
    val isLoading: Boolean = false
)
