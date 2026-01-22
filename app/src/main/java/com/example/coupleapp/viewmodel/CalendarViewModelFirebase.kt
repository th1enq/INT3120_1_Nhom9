package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.CalendarCacheRepository
import com.example.coupleapp.data.repository.CachedCalendarEvent
import com.example.coupleapp.data.repository.CachedCalendarProfile
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
 * 
 * Uses Cache-First Strategy:
 * 1. On init: Load cached data immediately (instant UI)
 * 2. Background refresh: Load fresh data from Firebase
 * 3. Cache duration: 1 hour for profile, 30 minutes for events
 */
class CalendarViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val calendarCache = CalendarCacheRepository.getInstance()

    companion object {
        private const val TAG = "CalendarViewModelFB"
    }

    // UI State
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // Live counter update job
    private var counterUpdateJob: Job? = null
    
    // Track current coupleId for caching
    private var currentCoupleId: String? = null

    init {
        loadDataWithCache()
        startLiveCounter()
    }

    /**
     * Load data with cache-first strategy
     */
    private fun loadDataWithCache() {
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false) }
                Log.e(TAG, "User not logged in")
                return@launch
            }

            try {
                // Get user to find coupleId
                val userResult = firestoreRepository.getDocument("users", userId, FirebaseUser::class.java)
                val currentUser = userResult.getOrNull()
                val coupleId = currentUser?.coupleId
                
                if (coupleId.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                currentCoupleId = coupleId
                
                // Try to load from cache first
                val hasCached = calendarCache.hasCachedData(coupleId)
                
                if (hasCached) {
                    Log.d(TAG, "📦 Cache found! Loading from cache first...")
                    loadFromCacheAndSyncBackground(coupleId, currentUser)
                } else {
                    Log.d(TAG, "🌐 No cache, loading from Firebase...")
                    _uiState.update { it.copy(isLoading = true) }
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadDataWithCache", e)
                _uiState.update { it.copy(isLoading = true) }
                loadData()
            }
        }
    }
    
    /**
     * Load from cache immediately, then sync in background
     */
    private suspend fun loadFromCacheAndSyncBackground(coupleId: String, currentUser: FirebaseUser) {
        try {
            val cachedProfile = calendarCache.getCachedCoupleProfile(coupleId)
            val cachedEvents = calendarCache.getCachedEvents(coupleId)
            
            if (cachedProfile != null) {
                Log.d(TAG, "📦 Using cached couple profile")
                
                // Convert cached profile to domain model
                val coupleProfile = cachedProfile.toCoupleProfile()
                
                // Update UI with cached data immediately
                _uiState.update {
                    it.copy(
                        coupleProfile = coupleProfile,
                        isLoading = false,
                        settings = it.settings.copy(
                            backgroundImageUrl = coupleProfile.backgroundImageUrl,
                            useDefaultBackground = coupleProfile.backgroundImageUrl.isEmpty()
                        )
                    )
                }
                updateLoveDaysCounter(coupleProfile.relationshipStartDate)
                
                // Process cached events
                if (cachedEvents != null) {
                    val anniversaries = cachedEvents.mapNotNull { it.toAnniversary() }
                    _uiState.update { it.copy(allAnniversaries = anniversaries) }
                    updateUpcomingEvents(anniversaries)
                    updateCalendarEvents(anniversaries)
                    Log.d(TAG, "📦 Loaded ${anniversaries.size} cached events")
                }
                
                // Check if cache is stale and refresh in background
                val isProfileFresh = calendarCache.isProfileCacheFresh(coupleId)
                val isEventsFresh = calendarCache.isEventsCacheFresh(coupleId)
                
                if (!isProfileFresh || !isEventsFresh) {
                    Log.d(TAG, "🔄 Cache is stale, refreshing in background...")
                    loadDataFromFirebase(showLoading = false)
                }
            } else {
                // No usable cache, load from Firebase
                loadData()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from cache", e)
            loadData()
        }
    }
    
    /**
     * Load data from Firebase and update cache
     */
    private fun loadDataFromFirebase(showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }
        loadData()
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
                        Log.d(TAG, "[CALENDAR] Partner ID: $partnerId")
                        if (!partnerId.isNullOrEmpty()) {
                            val partnerResult = firestoreRepository.getDocument(
                                "users",
                                partnerId,
                                FirebaseUser::class.java
                            )

                            partnerResult.fold(
                                onSuccess = { partner ->
                                    if (partner != null) {
                                        Log.d(TAG, "[CALENDAR] Partner loaded: ${partner.displayName}")
                                        // Load couple data to get nicknames and anniversary date
                                        val coupleData = if (!coupleIdToUse.isNullOrEmpty() && coupleIdToUse != userId) {
                                            Log.d(TAG, "[CALENDAR] Loading couple data from couples/$coupleIdToUse")
                                            val result = firestoreRepository.getDocument(
                                                "couples",
                                                coupleIdToUse,
                                                FirebaseCouple::class.java
                                            ).getOrNull()
                                            Log.d(TAG, "[CALENDAR] Couple data loaded: user1Nick=${result?.user1Nickname}, user2Nick=${result?.user2Nickname}, anniversaryDate=${result?.anniversaryDate}")
                                            result
                                        } else {
                                            Log.d(TAG, "[CALENDAR] No coupleId or coupleId == userId, skipping couple data load")
                                            null
                                        }

                                        val coupleProfile = createCoupleProfile(currentUser, partner, coupleData)
                                        Log.d(TAG, "[CALENDAR] Couple profile created: ${coupleProfile.user1.nickname} & ${coupleProfile.user2.nickname}, startDate=${coupleProfile.relationshipStartDate}")
                                        _uiState.update {
                                            it.copy(
                                                coupleProfile = coupleProfile,
                                                isLoading = false,
                                                settings = it.settings.copy(
                                                    backgroundImageUrl = coupleProfile.backgroundImageUrl,
                                                    useDefaultBackground = coupleProfile.backgroundImageUrl.isEmpty()
                                                )
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
     * Create couple profile from Firebase users and couple data
     */
    private fun createCoupleProfile(user1: FirebaseUser, user2: FirebaseUser, coupleData: FirebaseCouple? = null): CoupleProfile {
        // Get nicknames from couple data or use default
        val user1Nickname = coupleData?.let {
            if (it.user1Id == user1.id) it.user1Nickname else it.user2Nickname
        } ?: user1.displayName.split(" ").firstOrNull() ?: "Bạn"

        val user2Nickname = coupleData?.let {
            if (it.user2Id == user2.id) it.user2Nickname else it.user1Nickname
        } ?: user2.displayName.split(" ").firstOrNull() ?: "Partner"

        val user1Profile = CalendarUserProfile(
            id = user1.id,
            name = user1.displayName,
            nickname = user1Nickname,
            avatarUrl = user1.profileImageUrl,
            dateOfBirth = parseDateString(user1.dateOfBirth),
            zodiacSign = ZodiacSign.fromDate(parseDateString(user1.dateOfBirth))
        )

        val user2Profile = CalendarUserProfile(
            id = user2.id,
            name = user2.displayName,
            nickname = user2Nickname,
            avatarUrl = user2.profileImageUrl,
            dateOfBirth = parseDateString(user2.dateOfBirth),
            zodiacSign = ZodiacSign.fromDate(parseDateString(user2.dateOfBirth))
        )

        // Get relationship start date from couple data or use default
        val startDate = if (coupleData != null && coupleData.anniversaryDate.isNotEmpty()) {
            try {
                val date = LocalDate.parse(coupleData.anniversaryDate, DateTimeFormatter.ISO_LOCAL_DATE)
                date.atStartOfDay()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing anniversary date: ${coupleData.anniversaryDate}", e)
                LocalDateTime.now().minusDays(365)
            }
        } else {
            LocalDateTime.now().minusDays(365)
        }

        // Get background image URL from couple data
        val backgroundUrl = coupleData?.backgroundImageUrl ?: ""

        return CoupleProfile(
            user1 = user1Profile,
            user2 = user2Profile,
            relationshipStartDate = startDate,
            backgroundImageUrl = backgroundUrl
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
                    
                    // Cache the events
                    try {
                        val cachedEvents = anniversaries.map { it.toCachedEvent() }
                        calendarCache.cacheEvents(coupleId, cachedEvents)
                        Log.d(TAG, "📦 Cached ${cachedEvents.size} calendar events")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error caching events", e)
                    }
                    
                    _uiState.update { it.copy(allAnniversaries = anniversaries) }
                    updateUpcomingEvents(anniversaries)
                    updateCalendarEvents(anniversaries)
                    
                    // Also cache the couple profile if available
                    val coupleProfile = _uiState.value.coupleProfile
                    if (coupleProfile != null) {
                        try {
                            calendarCache.cacheCoupleProfile(coupleId, coupleProfile.toCachedProfile(coupleId))
                            Log.d(TAG, "📦 Cached couple profile")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error caching couple profile", e)
                        }
                    }
                    
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
     * Start live counter - updates once per minute (since we only count full days)
     */
    private fun startLiveCounter() {
        counterUpdateJob?.cancel()
        counterUpdateJob = viewModelScope.launch {
            while (true) {
                _uiState.value.coupleProfile?.let { profile ->
                    updateLoveDaysCounter(profile.relationshipStartDate)
                }
                delay(60_000) // Update every minute (sufficient since we count full days only)
            }
        }
    }

    /**
     * Update love days counter - only count full days from 00:00
     */
    private fun updateLoveDaysCounter(startDate: LocalDateTime) {
        val today = LocalDate.now()
        val startDateOnly = startDate.toLocalDate()
        
        // Calculate days between start date and today (not including time)
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDateOnly, today)
        val years = (totalDays / 365).toInt()
        val months = ((totalDays % 365) / 30).toInt()
        val days = ((totalDays % 365) % 30).toInt()

        val counter = LoveDaysCounter(
            totalDays = totalDays,
            years = years,
            months = months,
            days = days,
            hours = 0,
            minutes = 0,
            seconds = 0,
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
        val selectedDate = date ?: LocalDate.now()
        Log.d(TAG, "[CALENDAR] ▶️ showAddEventDialog called: selectedDate=$selectedDate")
        _uiState.update {
            it.copy(
                showAddEventDialog = true,
                selectedDate = selectedDate,
                editingAnniversary = null
            )
        }
        Log.d(TAG, "[CALENDAR] Add event dialog opened for date: $selectedDate")
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
        Log.d(TAG, "[CALENDAR] ▶️ saveAnniversary called: id=${anniversary.id}, title=${anniversary.title}, date=${anniversary.date}")
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "[CALENDAR] ❌ User not logged in")
                return@launch
            }
            Log.d(TAG, "[CALENDAR] Current userId: $userId")
            
            // Get real coupleId from user
            val userResult = firestoreRepository.getDocument(
                "users",
                userId,
                FirebaseUser::class.java
            )

            val coupleId = userResult.getOrNull()?.coupleId
            Log.d(TAG, "[CALENDAR] User's coupleId: $coupleId")
            if (coupleId.isNullOrEmpty()) {
                Log.e(TAG, "[CALENDAR] ❌ No coupleId found, cannot save anniversary")
                return@launch
            }

            val eventId = anniversary.id.ifEmpty { UUID.randomUUID().toString() }
            val firebaseEvent = FirebaseCalendarEvent(
                id = eventId,
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
            
            Log.d(TAG, "[CALENDAR] Saving event to calendar_events/$eventId with coupleId=$coupleId")
            Log.d(TAG, "[CALENDAR] Event details: title=${firebaseEvent.title}, date=${firebaseEvent.date}, type=${firebaseEvent.eventType}")

            firestoreRepository.setDocument(
                collection = "calendar_events",
                documentId = firebaseEvent.id,
                data = firebaseEvent
            ).fold(
                onSuccess = {
                    Log.d(TAG, "[CALENDAR] ✅ Anniversary saved successfully: $eventId")
                    loadAnniversaries(coupleId)
                    hideEventDialog()
                },
                onFailure = { error ->
                    Log.e(TAG, "[CALENDAR] ❌ Error saving anniversary: ${error.message}", error)
                }
            )
        }
    }

    /**
     * Delete anniversary
     */
    fun deleteAnniversary(anniversaryId: String) {
        Log.d(TAG, "[CALENDAR] ▶️ deleteAnniversary called: anniversaryId=$anniversaryId")
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "[CALENDAR] ❌ User not logged in")
                    return@launch
                }
                
                // Get coupleId for reloading
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )
                val coupleId = userResult.getOrNull()?.coupleId ?: ""
                Log.d(TAG, "[CALENDAR] Deleting event from calendar_events/$anniversaryId")
                
                firestoreRepository.deleteDocument(
                    collection = "calendar_events",
                    documentId = anniversaryId
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "[CALENDAR] ✅ Anniversary deleted successfully: $anniversaryId")
                        if (coupleId.isNotEmpty()) {
                            loadAnniversaries(coupleId)
                        }
                        hideEventDialog()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "[CALENDAR] ❌ Error deleting anniversary: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "[CALENDAR] ❌ Exception deleting anniversary: ${e.message}", e)
            }
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
     * Update nickname for a user - saves to couples collection
     */
    fun updateNickname(userId: String, newNickname: String) {
        Log.d(TAG, "[CALENDAR] ▶️ updateNickname called: userId=$userId, newNickname=$newNickname")
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.currentUser?.uid
                if (currentUserId == null) {
                    Log.e(TAG, "[CALENDAR] ❌ User not logged in")
                    return@launch
                }
                Log.d(TAG, "[CALENDAR] Current userId: $currentUserId")
                
                // Load current user to get coupleId
                val userResult = firestoreRepository.getDocument(
                    "users",
                    currentUserId,
                    FirebaseUser::class.java
                )

                val user = userResult.getOrNull()
                val coupleId = user?.coupleId
                val partnerId = user?.partnerId
                Log.d(TAG, "[CALENDAR] User's coupleId: $coupleId, partnerId: $partnerId")
                if (coupleId.isNullOrEmpty()) {
                    Log.e(TAG, "[CALENDAR] ❌ No coupleId found, cannot update nickname")
                    return@launch
                }

                // Check if couple document exists, create if not
                val coupleResult = firestoreRepository.getDocument(
                    "couples",
                    coupleId,
                    FirebaseCouple::class.java
                )
                
                if (coupleResult.isFailure || coupleResult.getOrNull() == null) {
                    Log.d(TAG, "[CALENDAR] Couple document doesn't exist, creating it...")
                    // Create couple document
                    val sortedIds = listOf(currentUserId, partnerId ?: "").sorted()
                    val newCouple = FirebaseCouple(
                        id = coupleId,
                        user1Id = sortedIds[0],
                        user2Id = sortedIds[1],
                        user1Nickname = "",
                        user2Nickname = "",
                        anniversaryDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                        relationshipStatus = "dating",
                        sharedGardenLevel = 1,
                        sharedPoints = 0,
                        createdAt = java.util.Date()
                    )
                    firestoreRepository.setDocument(
                        "couples",
                        coupleId,
                        newCouple
                    )
                    Log.d(TAG, "[CALENDAR] Created couple document: $coupleId")
                }

                // Update nickname in couples document
                val fieldName = if (userId == currentUserId) "user1Nickname" else "user2Nickname"
                Log.d(TAG, "[CALENDAR] Updating field '$fieldName' in couples/$coupleId with value: $newNickname")
                
                firestoreRepository.updateDocument(
                    "couples",
                    coupleId,
                    mapOf(fieldName to newNickname)
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "[CALENDAR] ✅ Nickname updated successfully")
                        // Reload data to reflect changes
                        loadData()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "[CALENDAR] ❌ Error updating nickname: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "[CALENDAR] ❌ Exception updating nickname: ${e.message}", e)
            }
        }
    }

    /**
     * Update relationship start date - saves to couples collection
     */
    fun updateRelationshipStartDate(newDate: LocalDate) {
        Log.d(TAG, "[CALENDAR] ▶️ updateRelationshipStartDate called: newDate=$newDate")
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "[CALENDAR] ❌ User not logged in")
                    return@launch
                }
                Log.d(TAG, "[CALENDAR] Current userId: $userId")
                
                // Load current user to get coupleId
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                val user = userResult.getOrNull()
                val coupleId = user?.coupleId
                val partnerId = user?.partnerId
                Log.d(TAG, "[CALENDAR] User's coupleId: $coupleId, partnerId: $partnerId")
                if (coupleId.isNullOrEmpty()) {
                    Log.e(TAG, "[CALENDAR] ❌ No coupleId found, cannot update anniversary date")
                    return@launch
                }

                // Check if couple document exists, create if not
                val coupleResult = firestoreRepository.getDocument(
                    "couples",
                    coupleId,
                    FirebaseCouple::class.java
                )
                
                if (coupleResult.isFailure || coupleResult.getOrNull() == null) {
                    Log.d(TAG, "[CALENDAR] Couple document doesn't exist, creating it...")
                    // Create couple document with the new anniversary date
                    val sortedIds = listOf(userId, partnerId ?: "").sorted()
                    val dateString = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val newCouple = FirebaseCouple(
                        id = coupleId,
                        user1Id = sortedIds[0],
                        user2Id = sortedIds[1],
                        user1Nickname = "",
                        user2Nickname = "",
                        anniversaryDate = dateString,
                        relationshipStatus = "dating",
                        sharedGardenLevel = 1,
                        sharedPoints = 0,
                        createdAt = java.util.Date()
                    )
                    firestoreRepository.setDocument(
                        "couples",
                        coupleId,
                        newCouple
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "[CALENDAR] ✅ Created couple document and set anniversary date: $dateString")
                            loadData()
                        },
                        onFailure = { error ->
                            Log.e(TAG, "[CALENDAR] ❌ Error creating couple document: ${error.message}", error)
                        }
                    )
                    return@launch
                }

                // Update anniversaryDate in existing couples document
                val dateString = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                Log.d(TAG, "[CALENDAR] Updating anniversaryDate in couples/$coupleId with value: $dateString")
                
                firestoreRepository.updateDocument(
                    "couples",
                    coupleId,
                    mapOf("anniversaryDate" to dateString)
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "[CALENDAR] ✅ Anniversary date updated successfully to: $dateString")
                        // Reload data to reflect changes
                        loadData()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "[CALENDAR] ❌ Error updating anniversary date: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "[CALENDAR] ❌ Exception updating anniversary date: ${e.message}", e)
            }
        }
    }

    /**
     * Update background image URL - saves to couples collection
     */
    fun updateBackgroundImage(imageUrl: String) {
        Log.d(TAG, "[CALENDAR] ▶️ updateBackgroundImage called: imageUrl=$imageUrl")
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "[CALENDAR] ❌ User not logged in")
                    return@launch
                }
                
                // Load current user to get coupleId
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )

                val user = userResult.getOrNull()
                val coupleId = user?.coupleId
                if (coupleId.isNullOrEmpty()) {
                    Log.e(TAG, "[CALENDAR] ❌ No coupleId found, cannot update background")
                    return@launch
                }

                // Update backgroundImageUrl in couples document
                firestoreRepository.updateDocument(
                    "couples",
                    coupleId,
                    mapOf("backgroundImageUrl" to imageUrl)
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "[CALENDAR] ✅ Background image updated successfully")
                        // Update local UI state
                        _uiState.update { state ->
                            state.copy(
                                coupleProfile = state.coupleProfile?.copy(backgroundImageUrl = imageUrl),
                                settings = state.settings.copy(
                                    backgroundImageUrl = imageUrl,
                                    useDefaultBackground = imageUrl.isEmpty()
                                )
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "[CALENDAR] ❌ Error updating background: ${error.message}", error)
                        _uiState.update { it.copy(errorMessage = "Lỗi cập nhật hình nền: ${error.message}") }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "[CALENDAR] ❌ Exception updating background: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "Lỗi: ${e.message}") }
            }
        }
    }

    /**
     * Toggle heartbeat animation setting
     */
    fun toggleHeartbeatAnimation(enabled: Boolean) {
        Log.d(TAG, "[CALENDAR] ▶️ toggleHeartbeatAnimation called: enabled=$enabled")
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(showHeartbeatAnimation = enabled)
            )
        }
    }

    /**
     * Update reminder hours setting
     */
    fun updateReminderHours(hours: Int) {
        Log.d(TAG, "[CALENDAR] ▶️ updateReminderHours called: hours=$hours")
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(reminderHoursBefore = hours)
            )
        }
        // TODO: Update notification scheduling based on new reminder hours
    }

    override fun onCleared() {
        super.onCleared()
        counterUpdateJob?.cancel()
    }
}

// ============ Cache Extension Functions ============

/**
 * Convert CachedCalendarProfile to CoupleProfile domain model
 */
private fun CachedCalendarProfile.toCoupleProfile(): CoupleProfile {
    val user1 = CalendarUserProfile(
        id = user1Id,
        name = user1Name,
        nickname = user1Nickname,
        avatarUrl = user1AvatarUrl,
        dateOfBirth = user1DateOfBirth?.let { 
            try { LocalDate.parse(it) } catch (e: Exception) { LocalDate.now().minusYears(25) }
        } ?: LocalDate.now().minusYears(25),
        zodiacSign = ZodiacSign.UNKNOWN
    )
    
    val user2 = CalendarUserProfile(
        id = user2Id,
        name = user2Name,
        nickname = user2Nickname,
        avatarUrl = user2AvatarUrl,
        dateOfBirth = user2DateOfBirth?.let { 
            try { LocalDate.parse(it) } catch (e: Exception) { LocalDate.now().minusYears(25) }
        } ?: LocalDate.now().minusYears(25),
        zodiacSign = ZodiacSign.UNKNOWN
    )
    
    val startDate = try {
        LocalDateTime.parse(relationshipStartDate)
    } catch (e: Exception) {
        LocalDateTime.now().minusDays(365)
    }
    
    return CoupleProfile(
        user1 = user1,
        user2 = user2,
        relationshipStartDate = startDate,
        backgroundImageUrl = backgroundImageUrl
    )
}

/**
 * Convert CachedCalendarEvent to Anniversary domain model
 */
private fun CachedCalendarEvent.toAnniversary(): Anniversary? {
    return try {
        val date = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE)
        val dateTime = if (!this.time.isNullOrEmpty()) {
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
            date = dateTime,
            type = type,
            isRecurring = this.isRecurring,
            reminderEnabled = this.reminderMinutes > 0
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Convert CoupleProfile to CachedCalendarProfile for storage
 */
private fun CoupleProfile.toCachedProfile(coupleId: String): CachedCalendarProfile {
    return CachedCalendarProfile(
        coupleId = coupleId,
        user1Id = user1.id,
        user1Name = user1.name,
        user1Nickname = user1.nickname,
        user1AvatarUrl = user1.avatarUrl,
        user1DateOfBirth = user1.dateOfBirth.toString(),
        user2Id = user2.id,
        user2Name = user2.name,
        user2Nickname = user2.nickname,
        user2AvatarUrl = user2.avatarUrl,
        user2DateOfBirth = user2.dateOfBirth.toString(),
        relationshipStartDate = relationshipStartDate.toString(),
        backgroundImageUrl = backgroundImageUrl
    )
}

/**
 * Convert Anniversary to CachedCalendarEvent for storage
 */
private fun Anniversary.toCachedEvent(): CachedCalendarEvent {
    return CachedCalendarEvent(
        id = id,
        title = title,
        description = description,
        date = date.toLocalDate().toString(),
        time = if (date.hour > 0 || date.minute > 0) "${date.hour}:${date.minute.toString().padStart(2, '0')}" else null,
        eventType = type.name.lowercase(),
        isRecurring = isRecurring,
        reminderMinutes = if (reminderEnabled) 60 else 0
    )
}
