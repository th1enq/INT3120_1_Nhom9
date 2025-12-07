package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ViewModel for Moments screen
 */
class MomentsViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(MomentsUiState())
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()
    
    init {
        loadMoments()
    }
    
    /**
     * Load all moments from data sources
     */
    fun loadMoments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Simulate loading delay
            delay(800)
            
            // Load sample data
            val moments = generateSampleMoments()
            val groupedMoments = groupMomentsByDate(moments)
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    momentsGroups = groupedMoments,
                    error = null
                )
            }
        }
    }
    
    /**
     * Refresh moments data
     */
    fun refreshMoments() {
        loadMoments()
    }
    
    /**
     * Group moments by date for timeline display
     */
    private fun groupMomentsByDate(moments: List<MomentItem>): List<MomentsGroup> {
        val grouped = moments
            .sortedByDescending { it.timestamp }
            .groupBy { it.timestamp.toLocalDate() }
        
        return grouped.map { (date, items) ->
            MomentsGroup(
                section = TimelineSection.from(date),
                moments = items
            )
        }
    }
    
    /**
     * Generate sample moments data
     * TODO: Replace with actual data from repositories
     */
    private fun generateSampleMoments(): List<MomentItem> {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        
        return listOf(
            // Today's sleep
            SleepMoment(
                id = "sleep1",
                timestamp = now.minusHours(6),
                userName = "Banana",
                userAvatar = "🍌",
                bedTime = LocalTime.of(3, 44),
                wakeUpTime = LocalTime.of(10, 31),
                sleepDuration = 406, // 6h 46min
                quality = SleepQuality.EXCELLENT,
                achievementPercentage = 84f
            ),
            
            // Missing sent
            MissingMoment(
                id = "missing1",
                timestamp = now.minusHours(8),
                senderName = "Banana",
                senderAvatar = "🍌",
                receiverName = "Broccoli",
                receiverAvatar = "🥦",
                missCount = 3
            ),
            
            // Locket posted
            LocketMoment(
                id = "locket1",
                timestamp = now.minusHours(10),
                senderName = "Broccoli",
                senderAvatar = "🥦",
                locketType = LocketType.EMOJI,
                content = "❤️",
                caption = "Thinking of you!"
            ),
            
            // Yesterday's sleep
            SleepMoment(
                id = "sleep2",
                timestamp = now.minusDays(1).minusHours(12),
                userName = "Broccoli",
                userAvatar = "🥦",
                bedTime = LocalTime.of(1, 9),
                wakeUpTime = LocalTime.of(7, 31),
                sleepDuration = 381, // 6h 21min
                quality = SleepQuality.GOOD,
                achievementPercentage = 79f
            ),
            
            // Upcoming event
            EventMoment(
                id = "event1",
                timestamp = now.minusDays(1).minusHours(15),
                title = "Valentine's Day",
                description = "Valentine's Day celebration",
                eventDate = today.plusDays(68),
                eventType = MomentEventType.SPECIAL_DAY,
                daysUntil = 68
            ),
            
            // Anniversary
            AnniversaryMoment(
                id = "anniversary1",
                timestamp = now.minusDays(2),
                daysTogether = 365,
                monthsTogether = 12,
                yearsTogether = 1,
                user1Name = "Banana",
                user1Avatar = "🍌",
                user2Name = "Broccoli",
                user2Avatar = "🥦"
            ),
            
            // More locket
            LocketMoment(
                id = "locket2",
                timestamp = now.minusDays(3).minusHours(5),
                senderName = "Banana",
                senderAvatar = "🍌",
                locketType = LocketType.TEXT,
                content = "Good morning! ☀️",
                caption = null
            ),
            
            // More missing
            MissingMoment(
                id = "missing2",
                timestamp = now.minusDays(4).minusHours(2),
                senderName = "Broccoli",
                senderAvatar = "🥦",
                receiverName = "Banana",
                receiverAvatar = "🍌",
                missCount = 5
            ),
            
            // Event
            EventMoment(
                id = "event2",
                timestamp = now.minusDays(5),
                title = "Banana's Birthday",
                description = null,
                eventDate = today.plusDays(30),
                eventType = MomentEventType.BIRTHDAY,
                daysUntil = 30
            ),
            
            // Old sleep
            SleepMoment(
                id = "sleep3",
                timestamp = now.minusDays(6).minusHours(8),
                userName = "Banana",
                userAvatar = "🍌",
                bedTime = LocalTime.of(2, 15),
                wakeUpTime = LocalTime.of(9, 0),
                sleepDuration = 405,
                quality = SleepQuality.GOOD,
                achievementPercentage = 82f
            )
        )
    }
}

/**
 * UI state for Moments screen
 */
data class MomentsUiState(
    val isLoading: Boolean = true,
    val momentsGroups: List<MomentsGroup> = emptyList(),
    val error: String? = null
)
