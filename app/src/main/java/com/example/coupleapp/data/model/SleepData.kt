package com.example.coupleapp.data.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Sleep quality levels
 */
enum class SleepQuality {
    EXCELLENT,  // >= 90%
    GOOD,       // >= 75%
    POOR        // < 75%
}

/**
 * Sleep stage data
 */
data class SleepStage(
    val awakeDurationMinutes: Int,
    val sleepDurationMinutes: Int
)

/**
 * Single sleep record
 */
data class SleepRecord(
    val id: String,
    val date: LocalDateTime,
    val bedTime: LocalTime,
    val wakeUpTime: LocalTime,
    val actualSleepDuration: Int, // in minutes
    val targetSleepDuration: Int, // in minutes
    val sleepStages: SleepStage,
    val quality: SleepQuality,
    val achievementPercentage: Float,
    val userId: String
) {
    val qualityText: String
        get() = when (quality) {
            SleepQuality.EXCELLENT -> "Excellent"
            SleepQuality.GOOD -> "Good"
            SleepQuality.POOR -> "Poor"
        }
    
    val totalDuration: Int
        get() = sleepStages.awakeDurationMinutes + sleepStages.sleepDurationMinutes
}

/**
 * Sleep settings
 */
data class SleepSettings(
    val targetSleepDuration: Int, // in minutes (default: 480 = 8 hours)
    val idealBedTime: LocalTime,
    val idealWakeUpTime: LocalTime,
    val userId: String
)

/**
 * User profile for sleep tracker
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?
)
