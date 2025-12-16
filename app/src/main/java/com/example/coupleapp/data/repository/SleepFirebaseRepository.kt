package com.example.coupleapp.data.repository

import android.util.Log
import com.example.coupleapp.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

/**
 * Firebase Repository for Sleep Tracker
 */
class SleepFirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    companion object {
        private const val TAG = "SleepFirebaseRepo"
        private const val SLEEP_SETTINGS_COLLECTION = "sleep_settings"
        private const val SLEEP_RECORDS_COLLECTION = "sleep_records"
        private const val USERS_COLLECTION = "users"
    }
    
    /**
     * Get or create sleep settings for current user
     */
    suspend fun getSleepSettings(userId: String): Result<FirebaseSleepSettings> {
        return try {
            Log.d(TAG, "getSleepSettings: Loading for userId=$userId")
            val doc = firestore.collection(SLEEP_SETTINGS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (doc.exists()) {
                val settings = doc.toObject(FirebaseSleepSettings::class.java)
                Log.d(TAG, "getSleepSettings: Found existing settings - bedTime=${settings?.idealBedTimeHour}:${settings?.idealBedTimeMinute}, duration=${settings?.targetSleepDurationMinutes}")
                Result.success(settings ?: createDefaultSettings(userId))
            } else {
                // Create default settings
                val defaultSettings = createDefaultSettings(userId)
                firestore.collection(SLEEP_SETTINGS_COLLECTION)
                    .document(userId)
                    .set(defaultSettings)
                    .await()
                Result.success(defaultSettings)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSleepSettings: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update sleep settings
     */
    suspend fun updateSleepSettings(settings: FirebaseSleepSettings): Result<Unit> {
        return try {
            Log.d(TAG, "updateSleepSettings: Saving to Firebase - bedTime=${settings.idealBedTimeHour}:${settings.idealBedTimeMinute}, duration=${settings.targetSleepDurationMinutes}")
            firestore.collection(SLEEP_SETTINGS_COLLECTION)
                .document(settings.userId)
                .set(settings)
                .await()
            Log.d(TAG, "updateSleepSettings: Success - saved to Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateSleepSettings: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get today's sleep record
     */
    suspend fun getTodaySleepRecord(userId: String): Result<FirebaseSleepRecord?> {
        return try {
            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay()
            val endOfDay = today.plusDays(1).atStartOfDay()
            
            val startTimestamp = Timestamp(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()))
            val endTimestamp = Timestamp(Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant()))
            
            val snapshot = firestore.collection(SLEEP_RECORDS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .whereLessThan("date", endTimestamp)
                .limit(1)
                .get()
                .await()
            
            val record = snapshot.documents.firstOrNull()?.toObject(FirebaseSleepRecord::class.java)
            Result.success(record)
        } catch (e: Exception) {
            Log.e(TAG, "getTodaySleepRecord: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get sleep history (last N days)
     */
    suspend fun getSleepHistory(userId: String, days: Int = 7): Result<List<FirebaseSleepRecord>> {
        return try {
            val startDate = LocalDate.now().minusDays(days.toLong())
            val startTimestamp = Timestamp(Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
            
            val snapshot = firestore.collection(SLEEP_RECORDS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .get()
                .await()
            
            val records = snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseSleepRecord::class.java)
            }.sortedByDescending { it.date }
            
            Result.success(records)
        } catch (e: Exception) {
            Log.e(TAG, "getSleepHistory: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save or update sleep record
     */
    suspend fun saveSleepRecord(record: FirebaseSleepRecord): Result<String> {
        return try {
            val docRef = if (record.id.isEmpty()) {
                firestore.collection(SLEEP_RECORDS_COLLECTION).document()
            } else {
                firestore.collection(SLEEP_RECORDS_COLLECTION).document(record.id)
            }
            
            val recordToSave = record.copy(id = docRef.id)
            docRef.set(recordToSave).await()
            
            Log.d(TAG, "saveSleepRecord: Success ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "saveSleepRecord: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if it's time to sleep
     */
    fun checkTimeToSleep(targetBedTime: LocalTime): Pair<Boolean, String> {
        val now = LocalTime.now()
        val reminderStart = targetBedTime.minusMinutes(15)
        val reminderEnd = targetBedTime.plusMinutes(30)
        
        return when {
            now.isAfter(reminderStart) && now.isBefore(targetBedTime) -> {
                val minutesUntil = java.time.Duration.between(now, targetBedTime).toMinutes()
                Pair(true, "Time to sleep in $minutesUntil minutes!")
            }
            now.isAfter(targetBedTime) && now.isBefore(reminderEnd) -> {
                Pair(true, "It's past your bedtime!")
            }
            now.isBefore(reminderStart) -> {
                val duration = java.time.Duration.between(now, targetBedTime)
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                Pair(false, "Sleep in ${hours}h ${minutes}m")
            }
            else -> {
                Pair(false, "")
            }
        }
    }
    
    /**
     * Calculate sleep quality and achievement
     */
    fun calculateSleepQuality(actualMinutes: Int, targetMinutes: Int): Pair<SleepQuality, Float> {
        val percentage = (actualMinutes.toFloat() / targetMinutes) * 100f
        val quality = when {
            percentage >= 90f -> SleepQuality.EXCELLENT
            percentage >= 75f -> SleepQuality.GOOD
            else -> SleepQuality.POOR
        }
        return Pair(quality, percentage)
    }
    
    /**
     * Get user profile info
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile> {
        return try {
            val doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            val name = doc.getString("displayName") ?: "User"
            val avatarUrl = doc.getString("profileImageUrl")
            
            Result.success(UserProfile(userId, name, avatarUrl))
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get partner's user ID
     */
    suspend fun getPartnerId(): Result<String?> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
            
            val doc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()
            
            val partnerId = doc.getString("partnerId")
            Result.success(partnerId)
        } catch (e: Exception) {
            Log.e(TAG, "getPartnerId: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Realtime listener for sleep records
     */
    fun getSleepRecordsFlow(userId: String): Flow<List<FirebaseSleepRecord>> = callbackFlow {
        val listener = firestore.collection(SLEEP_RECORDS_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getSleepRecordsFlow: Error", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirebaseSleepRecord::class.java)
                }?.sortedByDescending { it.date } ?: emptyList()
                
                trySend(records)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Create default settings
     */
    private fun createDefaultSettings(userId: String): FirebaseSleepSettings {
        return FirebaseSleepSettings(
            id = userId,
            userId = userId,
            targetSleepDurationMinutes = 480, // 8 hours
            idealBedTimeHour = 22,
            idealBedTimeMinute = 0,
            idealWakeUpTimeHour = 6,
            idealWakeUpTimeMinute = 0
        )
    }
    
    /**
     * Convert Firebase models to app models
     */
    fun convertToSleepRecord(firebaseRecord: FirebaseSleepRecord): SleepRecord {
        val date = firebaseRecord.date?.toDate()?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()
            ?: LocalDateTime.now()
        
        return SleepRecord(
            id = firebaseRecord.id,
            date = date,
            bedTime = LocalTime.of(firebaseRecord.bedTimeHour, firebaseRecord.bedTimeMinute),
            wakeUpTime = LocalTime.of(firebaseRecord.wakeUpTimeHour, firebaseRecord.wakeUpTimeMinute),
            actualSleepDuration = firebaseRecord.actualSleepDurationMinutes,
            targetSleepDuration = firebaseRecord.targetSleepDurationMinutes,
            sleepStages = SleepStage(
                awakeDurationMinutes = firebaseRecord.awakeDurationMinutes,
                sleepDurationMinutes = firebaseRecord.sleepDurationMinutes
            ),
            quality = when (firebaseRecord.quality) {
                "EXCELLENT" -> SleepQuality.EXCELLENT
                "GOOD" -> SleepQuality.GOOD
                else -> SleepQuality.POOR
            },
            achievementPercentage = firebaseRecord.achievementPercentage,
            userId = firebaseRecord.userId
        )
    }
    
    fun convertToSleepSettings(firebaseSettings: FirebaseSleepSettings): SleepSettings {
        return SleepSettings(
            targetSleepDuration = firebaseSettings.targetSleepDurationMinutes,
            idealBedTime = LocalTime.of(firebaseSettings.idealBedTimeHour, firebaseSettings.idealBedTimeMinute),
            idealWakeUpTime = LocalTime.of(firebaseSettings.idealWakeUpTimeHour, firebaseSettings.idealWakeUpTimeMinute),
            userId = firebaseSettings.userId
        )
    }
}
