package com.example.coupleapp.data.repository

import com.example.coupleapp.data.model.*
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * Mock repository for Sleep Tracker data
 * This provides fake data for UI development
 * Replace with actual repository implementation later
 */
object SleepRepository {
    
    // Mock users
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
    
    // Mock settings
    private val mockSettings = SleepSettings(
        targetSleepDuration = 480, // 8 hours
        idealBedTime = LocalTime.of(22, 0),
        idealWakeUpTime = LocalTime.of(6, 0),
        userId = currentUser.id
    )
    
    private val partnerSettings = SleepSettings(
        targetSleepDuration = 420, // 7 hours
        idealBedTime = LocalTime.of(23, 0),
        idealWakeUpTime = LocalTime.of(6, 0),
        userId = partnerUser.id
    )
    
    /**
     * Generate mock sleep records for the last 7 days
     */
    private fun generateMockRecords(userId: String, settings: SleepSettings): List<SleepRecord> {
        return (0..6).map { daysAgo ->
            val date = LocalDateTime.now().minusDays(daysAgo.toLong())
            val actualSleep = settings.targetSleepDuration + Random.nextInt(-120, 60)
            val achievementPercentage = (actualSleep.toFloat() / settings.targetSleepDuration) * 100f
            
            val quality = when {
                achievementPercentage >= 90f -> SleepQuality.EXCELLENT
                achievementPercentage >= 75f -> SleepQuality.GOOD
                else -> SleepQuality.POOR
            }
            
            val bedTime = settings.idealBedTime.plusMinutes(Random.nextLong(-30, 30))
            val wakeUpTime = bedTime.plusMinutes(actualSleep.toLong())
            
            SleepRecord(
                id = "sleep_${userId}_$daysAgo",
                date = date,
                bedTime = bedTime,
                wakeUpTime = wakeUpTime,
                actualSleepDuration = actualSleep,
                targetSleepDuration = settings.targetSleepDuration,
                sleepStages = SleepStage(
                    awakeDurationMinutes = Random.nextInt(15, 45),
                    sleepDurationMinutes = actualSleep - Random.nextInt(15, 45)
                ),
                quality = quality,
                achievementPercentage = achievementPercentage,
                userId = userId
            )
        }
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
     * Get sleep settings for a user
     */
    fun getSleepSettings(userId: String): SleepSettings {
        return if (userId == currentUser.id) mockSettings else partnerSettings
    }
    
    /**
     * Get today's sleep record for a user
     */
    fun getTodaySleepRecord(userId: String): SleepRecord? {
        val settings = getSleepSettings(userId)
        return generateMockRecords(userId, settings).firstOrNull()
    }
    
    /**
     * Get sleep history for a user (last 7 days)
     */
    fun getSleepHistory(userId: String): List<SleepRecord> {
        val settings = getSleepSettings(userId)
        return generateMockRecords(userId, settings)
    }
    
    /**
     * Update sleep settings
     */
    fun updateSleepSettings(userId: String, settings: SleepSettings) {
        // TODO: Implement actual update logic
        // For now, this is just a placeholder
    }
    
    /**
     * Update sleep record
     */
    fun updateSleepRecord(record: SleepRecord) {
        // TODO: Implement actual update logic
        // For now, this is just a placeholder
    }
}
