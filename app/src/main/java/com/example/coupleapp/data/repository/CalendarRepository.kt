package com.example.coupleapp.data.repository

import com.example.coupleapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Repository for managing calendar and anniversary data
 * TODO: Replace with actual Room database implementation
 */
class CalendarRepository {
    
    private val _coupleProfile = MutableStateFlow(getMockCoupleProfile())
    val coupleProfile: Flow<CoupleProfile> = _coupleProfile.asStateFlow()
    
    private val _anniversaries = MutableStateFlow(getMockAnniversaries())
    val anniversaries: Flow<List<Anniversary>> = _anniversaries.asStateFlow()
    
    private val _settings = MutableStateFlow(CalendarSettings())
    val settings: Flow<CalendarSettings> = _settings.asStateFlow()
    
    /**
     * Calculate love days counter
     * Note: On the first day together (startDate == today), totalDays = 1 (not 0)
     * This is consistent with ProfileViewModel.calculateDaysTogether
     */
    fun calculateLoveDays(startDate: LocalDateTime): LoveDaysCounter {
        val now = LocalDateTime.now()
        val totalSeconds = ChronoUnit.SECONDS.between(startDate, now)
        
        // Add 1 because the first day counts as "Day 1", not "Day 0"
        val totalDays = ChronoUnit.DAYS.between(startDate, now) + 1
        val years = totalDays / 365
        val remainingDaysAfterYears = totalDays % 365
        val months = remainingDaysAfterYears / 30
        val days = remainingDaysAfterYears % 30
        
        val todayStart = LocalDateTime.of(now.toLocalDate(), java.time.LocalTime.MIN)
        val secondsSinceMidnight = ChronoUnit.SECONDS.between(todayStart, now)
        val hours = (secondsSinceMidnight / 3600).toInt()
        val minutes = ((secondsSinceMidnight % 3600) / 60).toInt()
        val seconds = (secondsSinceMidnight % 60).toInt()
        
        // Calculate progress for circular animation (e.g., progress in current year)
        val daysInCurrentYear = totalDays % 365
        val progressPercentage = (daysInCurrentYear / 365f).coerceIn(0f, 1f)
        
        return LoveDaysCounter(
            totalDays = totalDays,
            years = years.toInt(),
            months = months.toInt(),
            days = days.toInt(),
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            progressPercentage = progressPercentage
        )
    }
    
    /**
     * Get upcoming anniversaries
     */
    fun getUpcomingAnniversaries(): List<CalendarEvent> {
        val today = LocalDate.now()
        return _anniversaries.value
            .map { anniversary ->
                val eventDate = anniversary.date.toLocalDate()
                val daysUntil = ChronoUnit.DAYS.between(today, eventDate)
                CalendarEvent(
                    id = anniversary.id,
                    title = anniversary.title,
                    date = eventDate,
                    type = anniversary.type,
                    daysUntil = daysUntil
                )
            }
            .filter { it.daysUntil >= 0 }
            .sortedBy { it.daysUntil }
            .take(10)
    }
    
    /**
     * Get events for a specific month
     */
    fun getEventsForMonth(year: Int, month: Int): Map<LocalDate, List<Anniversary>> {
        val firstDayOfMonth = LocalDate.of(year, month, 1)
        val lastDayOfMonth = firstDayOfMonth.plusMonths(1).minusDays(1)
        
        return _anniversaries.value
            .filter { anniversary ->
                val eventDate = anniversary.date.toLocalDate()
                eventDate >= firstDayOfMonth && eventDate <= lastDayOfMonth
            }
            .groupBy { it.date.toLocalDate() }
    }
    
    /**
     * Add new anniversary
     */
    suspend fun addAnniversary(anniversary: Anniversary) {
        val currentList = _anniversaries.value.toMutableList()
        currentList.add(anniversary)
        _anniversaries.value = currentList.sortedBy { it.date }
    }
    
    /**
     * Update existing anniversary
     */
    suspend fun updateAnniversary(anniversary: Anniversary) {
        val currentList = _anniversaries.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == anniversary.id }
        if (index != -1) {
            currentList[index] = anniversary
            _anniversaries.value = currentList.sortedBy { it.date }
        }
    }
    
    /**
     * Delete anniversary
     */
    suspend fun deleteAnniversary(anniversaryId: String) {
        val currentList = _anniversaries.value.toMutableList()
        currentList.removeAll { it.id == anniversaryId }
        _anniversaries.value = currentList
    }
    
    /**
     * Update couple profile
     */
    suspend fun updateCoupleProfile(profile: CoupleProfile) {
        _coupleProfile.value = profile
    }
    
    /**
     * Update user profile (nickname, avatar, etc.)
     */
    suspend fun updateUserProfile(userId: String, updatedProfile: CalendarUserProfile) {
        val currentProfile = _coupleProfile.value
        val newProfile = when (userId) {
            currentProfile.user1.id -> currentProfile.copy(user1 = updatedProfile)
            currentProfile.user2.id -> currentProfile.copy(user2 = updatedProfile)
            else -> currentProfile
        }
        _coupleProfile.value = newProfile
    }
    
    /**
     * Update calendar settings
     */
    suspend fun updateSettings(settings: CalendarSettings) {
        _settings.value = settings
    }
    
    /**
     * Update background image
     */
    suspend fun updateBackgroundImage(imageUrl: String) {
        val currentProfile = _coupleProfile.value
        _coupleProfile.value = currentProfile.copy(backgroundImageUrl = imageUrl)
    }
    
    // Mock data generators
    private fun getMockCoupleProfile(): CoupleProfile {
        return CoupleProfile(
            user1 = CalendarUserProfile(
                id = "user1",
                name = "Nguyễn Văn A",
                nickname = "Anh yêu",
                avatarUrl = "",
                dateOfBirth = LocalDate.of(1995, 3, 15),
                zodiacSign = ZodiacSign.fromDate(LocalDate.of(1995, 3, 15))
            ),
            user2 = CalendarUserProfile(
                id = "user2",
                name = "Trần Thị B",
                nickname = "Em yêu",
                avatarUrl = "",
                dateOfBirth = LocalDate.of(1997, 7, 20),
                zodiacSign = ZodiacSign.fromDate(LocalDate.of(1997, 7, 20))
            ),
            relationshipStartDate = LocalDateTime.of(2020, 2, 14, 18, 30),
            backgroundImageUrl = ""
        )
    }
    
    private fun getMockAnniversaries(): List<Anniversary> {
        val now = LocalDateTime.now()
        return listOf(
            Anniversary(
                id = "1",
                title = "Ngày yêu nhau",
                description = "Ngày chúng mình bắt đầu yêu nhau",
                date = LocalDateTime.of(2020, 2, 14, 18, 30),
                type = AnniversaryType.RELATIONSHIP_START,
                isRecurring = true
            ),
            Anniversary(
                id = "2",
                title = "Lần đầu gặp mặt",
                description = "Ngày đầu tiên gặp nhau tại quán cafe",
                date = LocalDateTime.of(2020, 1, 1, 15, 0),
                type = AnniversaryType.FIRST_MEET
            ),
            Anniversary(
                id = "3",
                title = "Sinh nhật em yêu",
                description = "Chúc mừng sinh nhật em",
                date = LocalDateTime.of(now.year, 7, 20, 0, 0),
                type = AnniversaryType.BIRTHDAY,
                isRecurring = true
            ),
            Anniversary(
                id = "4",
                title = "Sinh nhật anh yêu",
                description = "Chúc mừng sinh nhật anh",
                date = LocalDateTime.of(now.year, 3, 15, 0, 0),
                type = AnniversaryType.BIRTHDAY,
                isRecurring = true
            ),
            Anniversary(
                id = "5",
                title = "Kỷ niệm 1 năm yêu",
                description = "Tròn 1 năm bên nhau",
                date = LocalDateTime.of(2021, 2, 14, 18, 30),
                type = AnniversaryType.RELATIONSHIP_START
            ),
            Anniversary(
                id = "6",
                title = "Chuyến đi Đà Lạt",
                description = "Kỷ niệm chuyến đi đáng nhớ",
                date = LocalDateTime.of(now.year, 12, 25, 8, 0),
                type = AnniversaryType.TRIP
            )
        )
    }
}
