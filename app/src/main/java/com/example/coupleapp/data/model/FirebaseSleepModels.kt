package com.example.coupleapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firebase model for Sleep Settings
 */
data class FirebaseSleepSettings(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val targetSleepDurationMinutes: Int = 480, // 8 hours default
    val idealBedTimeHour: Int = 22,
    val idealBedTimeMinute: Int = 0,
    val idealWakeUpTimeHour: Int = 6,
    val idealWakeUpTimeMinute: Int = 0,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

/**
 * Firebase model for Sleep Record
 */
data class FirebaseSleepRecord(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val coupleId: String = "",
    val date: Timestamp? = null, // Date of sleep record
    val bedTimeHour: Int = 0,
    val bedTimeMinute: Int = 0,
    val wakeUpTimeHour: Int = 0,
    val wakeUpTimeMinute: Int = 0,
    val actualSleepDurationMinutes: Int = 0,
    val targetSleepDurationMinutes: Int = 480,
    val awakeDurationMinutes: Int = 0,
    val sleepDurationMinutes: Int = 0,
    val quality: String = "GOOD", // EXCELLENT, GOOD, POOR
    val achievementPercentage: Float = 0f,
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

/**
 * Firebase model for When to Sleep tracking
 */
data class FirebaseWhenToSleep(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val targetBedTimeHour: Int = 22,
    val targetBedTimeMinute: Int = 0,
    val currentTimeHour: Int = 0,
    val currentTimeMinute: Int = 0,
    val isTimeToSleep: Boolean = false,
    val hoursUntilBedtime: Int = 0,
    val minutesUntilBedtime: Int = 0,
    @ServerTimestamp
    val checkedAt: Timestamp? = null
)
