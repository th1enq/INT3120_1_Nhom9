package com.example.coupleapp.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class representing a daily missing summary for a user
 */
data class DailyMissingSummary(
    val date: LocalDate,
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val missCount: Int
) {
    /**
     * Get formatted date string (e.g., "Nov 24")
     */
    fun getFormattedDate(): String {
        return date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    
    /**
     * Get formatted month year (e.g., "November 24")
     */
    fun getFormattedMonthDay(): String {
        return date.format(DateTimeFormatter.ofPattern("MMMM d"))
    }
}

/**
 * Grouped history by date
 */
data class DailyMissingHistory(
    val date: LocalDate,
    val summaries: List<DailyMissingSummary>
) {
    fun getFormattedDate(): String {
        val now = LocalDate.now()
        return when {
            date == now -> "Today"
            date == now.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMMM d"))
        }
    }
}

/**
 * Data class representing a single missing record (for internal tracking)
 */
data class MissingRecord(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String? = null,
    val receiverId: String,
    val receiverName: String,
    val timestamp: LocalDateTime,
    val message: String? = null
)

/**
 * Summary data for missing feature
 * 
 * Streak logic:
 * - currentStreak: Number of consecutive days where BOTH users sent hearts
 * - hasSentToday: TRUE only when BOTH users have sent today (streak continues)
 * - meSentToday: TRUE when current user has sent today (for UI indication)
 * - partnerSentToday: TRUE when partner has sent today (for UI indication)
 */
data class MissingSummary(
    val totalMissCount: Int = 0,
    val todayMissCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val hasSentToday: Boolean = false, // TRUE when BOTH sent today
    val myTodayCount: Int = 0,
    val partnerTodayCount: Int = 0,
    // New fields for better UI indication
    val meSentToday: Boolean = false,    // TRUE when I have sent at least 1 heart today
    val partnerSentToday: Boolean = false // TRUE when partner has sent today
)

/**
 * User today's miss count display
 */
data class UserMissCount(
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val todayCount: Int
)
