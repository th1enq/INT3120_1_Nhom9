package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

/**
 * Firebase-integrated ViewModel for Calendar screen
 */
class CalendarViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    companion object {
        private const val TAG = "CalendarViewModelFB"
    }

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
     * Load initial data from Firebase
     */
    private fun loadData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false) }
                Log.e(TAG, "User not logged in")
                return@launch
            }

            try {
                // Load current user
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                userResult.fold(
                    onSuccess = { currentUser ->
                        if (currentUser == null) {
                            _uiState.update { it.copy(isLoading = false) }
                            return@fold
                        }

                        Log.d(TAG, "User loaded - coupleId: ${currentUser.coupleId}, partnerId: ${currentUser.partnerId}")

                        // Determine coupleId to use for loading events
                        val coupleIdToUse = if (!currentUser.coupleId.isNullOrEmpty()) {
                            currentUser.coupleId
                        } else {
                            userId // Fallback to userId if no coupleId
                        }

                        Log.d(TAG, "Loading anniversaries with coupleId: $coupleIdToUse")

                        // Load partner if exists
                        val partnerId = currentUser.partnerId
                        if (!partnerId.isNullOrEmpty()) {
                            val partnerResult = firestoreRepository.getDocument(
                                "users",
                                partnerId,
                                FirebaseUser::class.java
                            )

                            partnerResult.fold(
                                onSuccess = { partner ->
                                    if (partner != null) {
                                        val coupleProfile = createCoupleProfile(currentUser, partner)
                                        _uiState.update {
                                            it.copy(
                                                coupleProfile = coupleProfile,
                                                isLoading = false
                                            )
                                        }
                                        updateLoveDaysCounter(coupleProfile.relationshipStartDate)
                                    } else {
                                        _uiState.update { it.copy(isLoading = false) }
                                    }
                                    // Load anniversaries regardless of partner load success
                                    loadAnniversaries(coupleIdToUse!!)
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "Error loading partner: ${error.message}")
                                    _uiState.update { it.copy(isLoading = false) }
                                    // Still try to load anniversaries
                                    loadAnniversaries(coupleIdToUse!!)
                                }
                            )
                        } else {
                            Log.d(TAG, "No partner, loading anniversaries anyway")
                            _uiState.update { it.copy(isLoading = false) }
                            // Load anniversaries even without partner
                            loadAnniversaries(coupleIdToUse!!)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error loading user", error)
                        _uiState.update { it.copy(isLoading = false) }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Create couple profile from Firebase users
     */
    private fun createCoupleProfile(user1: FirebaseUser, user2: FirebaseUser): CoupleProfile {
        val user1Profile = CalendarUserProfile(
            id = user1.id,
            name = user1.displayName,
            nickname = user1.displayName.split(" ").firstOrNull() ?: "Bạn",
            avatarUrl = user1.profileImageUrl,
            dateOfBirth = parseDateString(user1.dateOfBirth),
            zodiacSign = ZodiacSign.fromDate(parseDateString(user1.dateOfBirth))
        )

        val user2Profile = CalendarUserProfile(
            id = user2.id,
            name = user2.displayName,
            nickname = user2.displayName.split(" ").firstOrNull() ?: "Partner",
            avatarUrl = user2.profileImageUrl,
            dateOfBirth = parseDateString(user2.dateOfBirth),
            zodiacSign = ZodiacSign.fromDate(parseDateString(user2.dateOfBirth))
        )

        // Default relationship start date
        val startDate = LocalDateTime.now().minusDays(365)

        return CoupleProfile(
            user1 = user1Profile,
            user2 = user2Profile,
            relationshipStartDate = startDate,
            backgroundImageUrl = ""
        )
    }

    /**
     * Parse date string to LocalDate
     */
    private fun parseDateString(dateString: String): LocalDate {
        return try {
            if (dateString.isNotEmpty()) {
                LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
            } else {
                LocalDate.now().minusYears(25)
            }
        } catch (e: Exception) {
            LocalDate.now().minusYears(25)
        }
    }

    /**
     * Load anniversaries from Firebase
     */
    private fun loadAnniversaries(coupleId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading anniversaries for coupleId: $coupleId")
            
            val result = firestoreRepository.queryDocuments(
                collection = "calendar_events",
                field = "coupleId",
                value = coupleId,
                clazz = FirebaseCalendarEvent::class.java
            )

            result.fold(
                onSuccess = { events ->
                    Log.d(TAG, "Loaded ${events.size} events from Firebase")
                    val anniversaries = events.mapNotNull { it.toAnniversary() }
                    Log.d(TAG, "Converted to ${anniversaries.size} anniversaries")
                    
                    _uiState.update { it.copy(allAnniversaries = anniversaries) }
                    updateUpcomingEvents(anniversaries)
                    updateCalendarEvents(anniversaries)
                    
                    Log.d(TAG, "Updated UI with anniversaries")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error loading anniversaries: ${error.message}", error)
                }
            )
        }
    }

    /**
     * Convert Firebase event to Anniversary
     */
    private fun FirebaseCalendarEvent.toAnniversary(): Anniversary? {
        return try {
            val date = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE)
            val time = if (this.time.isNotEmpty()) {
                LocalDateTime.parse("${this.date}T${this.time}:00")
            } else {
                date.atStartOfDay()
            }

            val type = when (this.eventType.lowercase()) {
                "date" -> AnniversaryType.FIRST_DATE
                "anniversary" -> AnniversaryType.RELATIONSHIP_START
                "birthday" -> AnniversaryType.BIRTHDAY
                "trip" -> AnniversaryType.TRIP
                else -> AnniversaryType.CUSTOM
            }

            Anniversary(
                id = this.id,
                title = this.title,
                description = this.description,
                date = time,
                type = type,
                isRecurring = this.isRecurring,
                reminderEnabled = this.reminderMinutes > 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting event to anniversary", e)
            null
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
                delay(1000)
            }
        }
    }

    /**
     * Update love days counter
     */
    private fun updateLoveDaysCounter(startDate: LocalDateTime) {
        val now = LocalDateTime.now()
        val duration = Duration.between(startDate, now)

        val totalDays = duration.toDays()
        val years = (totalDays / 365).toInt()
        val months = ((totalDays % 365) / 30).toInt()
        val days = ((totalDays % 365) % 30).toInt()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        val counter = LoveDaysCounter(
            totalDays = totalDays,
            years = years,
            months = months,
            days = days,
            hours = hours.toInt(),
            minutes = minutes.toInt(),
            seconds = seconds.toInt(),
            progressPercentage = (totalDays % 365).toFloat() / 365f
        )

        _uiState.update { it.copy(loveDaysCounter = counter) }
    }

    /**
     * Update upcoming events - shows all events (past and future)
     */
    private fun updateUpcomingEvents(anniversaries: List<Anniversary>) {
        val today = LocalDate.now()
        Log.d(TAG, "Updating events from ${anniversaries.size} anniversaries")
        
        val allEvents = anniversaries
            .map { anniversary ->
                val eventDate = anniversary.date.toLocalDate()
                val daysUntil = Duration.between(today.atStartOfDay(), eventDate.atStartOfDay()).toDays()
                CalendarEvent(
                    id = anniversary.id,
                    title = anniversary.title,
                    date = eventDate,
                    type = anniversary.type,
                    daysUntil = daysUntil
                )
            }
            .sortedBy { kotlin.math.abs(it.daysUntil) } // Sort by closest to today (past or future)

        Log.d(TAG, "Setting ${allEvents.size} events")
        allEvents.forEach { 
            val status = if (it.daysUntil >= 0) "in ${it.daysUntil} days" else "${-it.daysUntil} days ago"
            Log.d(TAG, "  - ${it.title} $status")
        }
        
        _uiState.update { it.copy(upcomingEvents = allEvents) }
    }

    /**
     * Update calendar events for current selected month
     */
    private fun updateCalendarEvents(anniversaries: List<Anniversary>) {
        val currentMonth = _uiState.value.selectedYearMonth
        val eventsMap = anniversaries
            .filter {
                val date = it.date.toLocalDate()
                date.year == currentMonth.year && date.monthValue == currentMonth.monthValue
            }
            .groupBy { it.date.toLocalDate() }

        _uiState.update { it.copy(calendarEvents = eventsMap) }
    }

    /**
     * Toggle view mode
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
     * Navigate to previous month
     */
    fun previousMonth() {
        val currentMonth = _uiState.value.selectedYearMonth
        val newMonth = currentMonth.minusMonths(1)
        _uiState.update { it.copy(selectedYearMonth = newMonth) }
        updateCalendarEvents(_uiState.value.allAnniversaries)
    }

    /**
     * Navigate to next month
     */
    fun nextMonth() {
        val currentMonth = _uiState.value.selectedYearMonth
        val newMonth = currentMonth.plusMonths(1)
        _uiState.update { it.copy(selectedYearMonth = newMonth) }
        updateCalendarEvents(_uiState.value.allAnniversaries)
    }

    /**
     * Select a date
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
     * Save anniversary to Firebase
     */
    fun saveAnniversary(anniversary: Anniversary) {
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid ?: return@launch
            
            // Get real coupleId from user
            val userResult = firestoreRepository.getDocument(
                "users",
                userId,
                FirebaseUser::class.java
            )

            val coupleId = userResult.getOrNull()?.coupleId
            if (coupleId.isNullOrEmpty()) {
                Log.e(TAG, "No coupleId found, cannot save anniversary")
                return@launch
            }

            val firebaseEvent = FirebaseCalendarEvent(
                id = anniversary.id.ifEmpty { UUID.randomUUID().toString() },
                coupleId = coupleId,
                title = anniversary.title,
                description = anniversary.description,
                date = anniversary.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                time = anniversary.date.format(DateTimeFormatter.ofPattern("HH:mm")),
                eventType = anniversary.type.name.lowercase(),
                isRecurring = anniversary.isRecurring,
                reminderMinutes = if (anniversary.reminderEnabled) 60 else 0,
                createdAt = Date()
            )

            firestoreRepository.setDocument(
                collection = "calendar_events",
                documentId = firebaseEvent.id,
                data = firebaseEvent
            ).fold(
                onSuccess = {
                    Log.d(TAG, "Anniversary saved successfully")
                    loadAnniversaries(coupleId)
                    hideEventDialog()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error saving anniversary", error)
                }
            )
        }
    }

    /**
     * Delete anniversary
     */
    fun deleteAnniversary(anniversaryId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteDocument(
                collection = "calendar_events",
                documentId = anniversaryId
            ).fold(
                onSuccess = {
                    Log.d(TAG, "Anniversary deleted successfully")
                    val coupleId = _uiState.value.coupleProfile?.let {
                        authRepository.currentUser?.uid ?: ""
                    } ?: ""
                    loadAnniversaries(coupleId)
                    hideEventDialog()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error deleting anniversary", error)
                }
            )
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
     * Show anniversary management
     */
    fun showAnniversaryManagement() {
        _uiState.update { it.copy(showAnniversaryManagement = true) }
    }

    /**
     * Hide anniversary management
     */
    fun hideAnniversaryManagement() {
        _uiState.update { it.copy(showAnniversaryManagement = false) }
    }

    /**
     * Insert mock calendar data for testing
     */
    fun insertMockCalendarData() {
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(errorMessage = "Bạn chưa đăng nhập") }
                    return@launch
                }
                
                Log.d(TAG, "Starting mock data insert for userId: $userId")

                // Get real coupleId from current user
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                userResult.fold(
                    onSuccess = { user ->
                        if (user == null) {
                            Log.e(TAG, "User data not found")
                            _uiState.update { it.copy(errorMessage = "Không tìm thấy thông tin user") }
                            return@fold
                        }

                        var coupleId = user.coupleId
                        
                        // If no coupleId, use userId as fallback (for testing without partner)
                        if (coupleId.isNullOrEmpty()) {
                            Log.w(TAG, "No coupleId found, using userId as coupleId for testing")
                            coupleId = userId
                        }

                        Log.d(TAG, "Using coupleId: $coupleId")

                        val today = LocalDate.now()
                        val mockEvents = listOf(
                            // Relationship anniversary (1 year ago)
                            Triple("Ngày yêu nhau", "Ngày chúng ta chính thức bên nhau ❤️", today.minusYears(1)),
                            // First date (1.5 years ago)
                            Triple("Hẹn hò đầu tiên", "Buổi hẹn đầu tiên tại quán cafe", today.minusMonths(18)),
                            // Upcoming birthday
                            Triple("Sinh nhật bạn", "Sinh nhật của người yêu 🎂", today.plusDays(15)),
                            // Recent trip
                            Triple("Chuyến đi Đà Lạt", "Kỷ niệm chuyến đi thành phố ngàn hoa", today.minusMonths(2)),
                            // Valentine's Day (next year)
                            Triple("Valentine 2026", "Ngày lễ tình nhân", LocalDate.of(2026, 2, 14)),
                            // Monthly anniversary (this month)
                            Triple("Kỷ niệm 1 tháng", "Tròn 1 tháng yêu nhau", today.plusDays(5)),
                            // Special moment (last week)
                            Triple("Khoảnh khắc đặc biệt", "Ngày ta nói lời yêu thương", today.minusDays(7))
                        )

                        val eventTypes = listOf("anniversary", "date", "birthday", "trip", "anniversary", "anniversary", "date")

                        var successCount = 0
                        var failCount = 0

                        mockEvents.forEachIndexed { index, (title, description, date) ->
                            val eventId = UUID.randomUUID().toString()
                            val event = FirebaseCalendarEvent(
                                id = eventId,
                                coupleId = coupleId,
                                title = title,
                                description = description,
                                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                time = "20:00",
                                eventType = eventTypes[index],
                                isRecurring = index == 0,
                                reminderMinutes = 1440,
                                createdAt = Date()
                            )

                            firestoreRepository.setDocument(
                                collection = "calendar_events",
                                documentId = eventId,
                                data = event
                            ).fold(
                                onSuccess = {
                                    successCount++
                                    Log.d(TAG, "✓ Mock event inserted: $title (id: $eventId)")
                                },
                                onFailure = { error ->
                                    failCount++
                                    Log.e(TAG, "✗ Error inserting: $title - ${error.message}")
                                }
                            )
                        }

                        // Wait for all inserts to complete
                        delay(1500)
                        
                        Log.d(TAG, "Insert completed: $successCount success, $failCount failed")
                        
                        // Reload data
                        loadAnniversaries(coupleId)
                        
                        _uiState.update { 
                            it.copy(errorMessage = "Đã thêm $successCount sự kiện mẫu") 
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error loading user data: ${error.message}")
                        _uiState.update { 
                            it.copy(errorMessage = "Lỗi: ${error.message}") 
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in insertMockCalendarData", e)
                _uiState.update { 
                    it.copy(errorMessage = "Lỗi: ${e.message}") 
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        counterUpdateJob?.cancel()
    }
}
