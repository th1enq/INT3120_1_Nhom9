package com.example.coupleapp.data.repository

import com.example.coupleapp.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Mock repository for Missing feature data
 * This provides fake data for UI development
 * Replace with actual repository implementation later
 */
object MissingRepository {

    // Mock users (consistent with other repositories)
    private val currentUser = UserProfile(
        id = "user1",
        name = "Emma",
        avatarUrl = null
    )

    private val partnerUser = UserProfile(
        id = "user2",
        name = "Alex",
        avatarUrl = null
    )

    // Track if user has sent today (for streak)
    private var hasSentToday = true
    private var myTodayCount = Random.nextInt(10, 50)
    private var partnerTodayCount = Random.nextInt(10, 50)
    private var currentStreak = Random.nextInt(5, 30)

    /**
     * Generate mock daily history for last 7 days
     */
    private fun generateMockDailyHistory(): List<DailyMissingHistory> {
        val histories = mutableListOf<DailyMissingHistory>()
        val today = LocalDate.now()
        
        // Generate data for last 7 days
        for (dayOffset in 0 until 7) {
            val date = today.minusDays(dayOffset.toLong())
            val summaries = mutableListOf<DailyMissingSummary>()
            
            // Random miss counts for each user on this day
            val currentUserCount = if (dayOffset == 0) myTodayCount else Random.nextInt(15, 100)
            val partnerCount = if (dayOffset == 0) partnerTodayCount else Random.nextInt(15, 100)
            
            // Add current user's summary
            summaries.add(
                DailyMissingSummary(
                    date = date,
                    userId = currentUser.id,
                    userName = currentUser.name,
                    userAvatar = currentUser.avatarUrl,
                    missCount = currentUserCount
                )
            )
            
            // Add partner's summary
            summaries.add(
                DailyMissingSummary(
                    date = date,
                    userId = partnerUser.id,
                    userName = partnerUser.name,
                    userAvatar = partnerUser.avatarUrl,
                    missCount = partnerCount
                )
            )
            
            histories.add(
                DailyMissingHistory(
                    date = date,
                    summaries = summaries
                )
            )
        }
        
        return histories
    }

    /**
     * Get current user profile
     */
    fun getCurrentUser(): UserProfile = currentUser

    /**
     * Get partner user profile
     */
    fun getPartnerUser(): UserProfile = partnerUser

    /**
     * Get missing history grouped by day (last 7 days)
     */
    fun getDailyMissingHistory(): List<DailyMissingHistory> = generateMockDailyHistory()

    /**
     * Get missing summary statistics
     */
    fun getMissingSummary(): MissingSummary {
        return MissingSummary(
            totalMissCount = Random.nextInt(200, 500),
            todayMissCount = myTodayCount + partnerTodayCount,
            currentStreak = if (hasSentToday) currentStreak else 0,
            longestStreak = Random.nextInt(30, 100),
            hasSentToday = hasSentToday,
            myTodayCount = myTodayCount,
            partnerTodayCount = partnerTodayCount
        )
    }

    /**
     * Get today's counts for both users
     */
    fun getTodayCounts(): Pair<UserMissCount, UserMissCount> {
        return Pair(
            UserMissCount(
                userId = currentUser.id,
                userName = currentUser.name,
                userAvatar = currentUser.avatarUrl,
                todayCount = myTodayCount
            ),
            UserMissCount(
                userId = partnerUser.id,
                userName = partnerUser.name,
                userAvatar = partnerUser.avatarUrl,
                todayCount = partnerTodayCount
            )
        )
    }

    /**
     * Send a missing signal
     */
    fun sendMissing(): MissingSummary {
        myTodayCount++
        if (!hasSentToday) {
            currentStreak++
        }
        hasSentToday = true
        
        return getMissingSummary()
    }

    /**
     * Check if streak is active (user has sent today)
     */
    fun isStreakActive(): Boolean = hasSentToday

    /**
     * Get current streak count
     */
    fun getCurrentStreak(): Int = if (hasSentToday) currentStreak else 0
}
