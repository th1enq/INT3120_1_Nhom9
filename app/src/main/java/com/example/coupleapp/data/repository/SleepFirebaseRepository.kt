package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.data.health.HealthConnectManager
import com.example.coupleapp.data.health.HealthSleepSession
import com.example.coupleapp.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
    private val context: Context? = null,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    private val healthConnectManager by lazy {
        context?.let { HealthConnectManager(it) }
    }
    
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
            
            Log.d(TAG, "getTodaySleepRecord: Querying for userId=$userId, date range: $startTimestamp to $endTimestamp")
            
            // Workaround: Get all user records and filter client-side until index is ready
            val snapshot = firestore.collection(SLEEP_RECORDS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            Log.d(TAG, "getTodaySleepRecord: Query returned ${snapshot.documents.size} total documents for user")
            
            // Log all documents for debugging
            snapshot.documents.forEach { doc ->
                val data = doc.data
                Log.d(TAG, "getTodaySleepRecord: Document - userId=${data?.get("userId")}, date=${data?.get("date")}, quality=${data?.get("quality")}")
            }
            
            // Filter client-side for today's date - skip invalid records
            val allRecords = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(FirebaseSleepRecord::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "getTodaySleepRecord: Failed to parse document ${doc.id}: ${e.message}")
                    null
                }
            }
            Log.d(TAG, "getTodaySleepRecord: Parsed ${allRecords.size} valid records from ${snapshot.documents.size} documents")
            
            val record = allRecords.firstOrNull { record ->
                val recordDate = record.date ?: return@firstOrNull false
                val match = recordDate.seconds >= startTimestamp.seconds && recordDate.seconds < endTimestamp.seconds
                Log.d(TAG, "getTodaySleepRecord: Checking record - date=${recordDate.seconds} (${startTimestamp.seconds} to ${endTimestamp.seconds}), match=$match")
                match
            }
            
            Log.d(TAG, "getTodaySleepRecord: Found today's record = $record")
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
            
            Log.d(TAG, "getSleepHistory: Querying for userId=$userId, days=$days, startDate=$startTimestamp")
            
            // Workaround: Get all user records and filter client-side until index is ready
            val snapshot = firestore.collection(SLEEP_RECORDS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            Log.d(TAG, "getSleepHistory: Query returned ${snapshot.documents.size} total documents")
            
            // Log sample documents for debugging
            snapshot.documents.take(3).forEach { doc ->
                val data = doc.data
                Log.d(TAG, "getSleepHistory: Sample doc - userId=${data?.get("userId")}, date=${data?.get("date")}")
            }
            
            // Filter client-side for date range - skip invalid records
            val records = snapshot.documents
                .mapNotNull { doc -> 
                    try {
                        doc.toObject(FirebaseSleepRecord::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "getSleepHistory: Failed to parse document ${doc.id}: ${e.message}")
                        null
                    }
                }
                .filter { record -> 
                    val recordDate = record.date ?: return@filter false
                    recordDate.seconds >= startTimestamp.seconds 
                }
                .sortedByDescending { it.date }
            
            Log.d(TAG, "getSleepHistory: Filtered to ${records.size} records in date range")
            records.take(3).forEach { record ->
                Log.d(TAG, "getSleepHistory: Record - userId=${record.userId}, date=${record.date}, quality=${record.quality}")
            }
            
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
     * Only show reminder if current time is after target bedtime
     */
    fun checkTimeToSleep(targetBedTime: LocalTime): Pair<Boolean, String> {
        val now = LocalTime.now()
        
        return when {
            // Show reminder only if current time is after bedtime
            now.isAfter(targetBedTime) -> {
                val minutesPast = java.time.Duration.between(targetBedTime, now).toMinutes()
                if (minutesPast < 30) {
                    Pair(true, "It's past your bedtime!")
                } else {
                    Pair(false, "")
                }
            }
            else -> {
                // Don't show reminder if it's before bedtime
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
    
    /**
     * Insert mock sleep data for testing (both user and partner)
     */
    suspend fun insertMockSleepData(userId: String, partnerId: String?): Result<Unit> {
        return try {
            Log.d(TAG, "insertMockSleepData: Inserting for user=$userId, partner=$partnerId")
            
            val today = LocalDate.now()
            
            data class MockSleepData(
                val bedHour: Int, val bedMin: Int,
                val wakeHour: Int, val wakeMin: Int,
                val quality: String, val achievement: Float
            )
            
            val mockDataList = mutableMapOf<String, List<MockSleepData>>()
            
            // Mock data for current user (last 7 days)
            mockDataList[userId] = listOf(
                MockSleepData(22, 30, 6, 15, "EXCELLENT", 95f),  // Today
                MockSleepData(23, 0, 7, 0, "GOOD", 85f),          // Yesterday
                MockSleepData(22, 15, 6, 30, "EXCELLENT", 98f),   // 2 days ago
                MockSleepData(23, 30, 5, 45, "POOR", 65f),        // 3 days ago
                MockSleepData(22, 0, 6, 0, "EXCELLENT", 100f),    // 4 days ago
                MockSleepData(23, 15, 6, 45, "GOOD", 88f),        // 5 days ago
                MockSleepData(22, 45, 6, 30, "EXCELLENT", 92f)    // 6 days ago
            )
            
            // Mock data for partner if exists
            if (partnerId != null) {
                mockDataList[partnerId] = listOf(
                    MockSleepData(21, 30, 5, 30, "EXCELLENT", 100f),
                    MockSleepData(22, 0, 6, 0, "EXCELLENT", 100f),
                    MockSleepData(21, 45, 5, 45, "EXCELLENT", 100f),
                    MockSleepData(22, 30, 6, 15, "GOOD", 90f),
                    MockSleepData(23, 0, 6, 30, "GOOD", 88f),
                    MockSleepData(22, 15, 6, 0, "EXCELLENT", 95f),
                    MockSleepData(21, 30, 5, 30, "EXCELLENT", 100f)
                )
            }
            
            // Insert data for each user
            for ((targetUserId, dataList) in mockDataList) {
                dataList.forEachIndexed { index, mockData ->
                    val date = today.minusDays(index.toLong())
                    val dateTime = date.atStartOfDay()
                    
                    // Calculate sleep duration
                    val bedTime = LocalTime.of(mockData.bedHour, mockData.bedMin)
                    val wakeTime = LocalTime.of(mockData.wakeHour, mockData.wakeMin)
                    val duration = if (wakeTime.isAfter(bedTime)) {
                        java.time.Duration.between(bedTime, wakeTime).toMinutes().toInt()
                    } else {
                        // Overnight sleep
                        (java.time.Duration.between(bedTime, LocalTime.MAX).toMinutes() +
                         java.time.Duration.between(LocalTime.MIN, wakeTime).toMinutes()).toInt()
                    }
                    
                    val recordId = "${targetUserId}_${date}"
                    val timestamp = Timestamp(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()))
                    val record = FirebaseSleepRecord(
                        id = recordId,
                        userId = targetUserId,
                        coupleId = "",
                        date = timestamp,
                        bedTimeHour = mockData.bedHour,
                        bedTimeMinute = mockData.bedMin,
                        wakeUpTimeHour = mockData.wakeHour,
                        wakeUpTimeMinute = mockData.wakeMin,
                        actualSleepDurationMinutes = duration,
                        targetSleepDurationMinutes = 480, // 8 hours
                        awakeDurationMinutes = (duration * 0.1).toInt(),
                        sleepDurationMinutes = (duration * 0.9).toInt(),
                        quality = mockData.quality,
                        achievementPercentage = mockData.achievement
                    )
                    
                    firestore.collection(SLEEP_RECORDS_COLLECTION)
                        .document(recordId)
                        .set(record)
                        .await()
                    
                    Log.d(TAG, "insertMockSleepData: Inserted record for $targetUserId on $date, timestamp=$timestamp, userId=${record.userId}")
                }
                
                // Create default settings if not exists
                val settingsDoc = firestore.collection(SLEEP_SETTINGS_COLLECTION)
                    .document(targetUserId)
                    .get()
                    .await()
                
                if (!settingsDoc.exists()) {
                    val settings = createDefaultSettings(targetUserId)
                    firestore.collection(SLEEP_SETTINGS_COLLECTION)
                        .document(targetUserId)
                        .set(settings)
                        .await()
                    Log.d(TAG, "insertMockSleepData: Created default settings for $targetUserId")
                }
            }
            
            Log.d(TAG, "insertMockSleepData: Successfully inserted all mock data")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "insertMockSleepData: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if Health Connect is available
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        return healthConnectManager?.isAvailable() ?: false
    }
    
    /**
     * Check if Health Connect permissions are granted
     */
    suspend fun hasHealthConnectPermissions(): Boolean {
        return healthConnectManager?.hasAllPermissions() ?: false
    }
    
    /**
     * Get Health Connect permission contract
     */
    fun getHealthConnectPermissionContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> {
        return healthConnectManager?.createPermissionRequestContract() 
            ?: throw IllegalStateException("Health Connect not initialized")
    }
    
    /**
     * Get required Health Connect permissions
     */
    fun getRequiredHealthConnectPermissions(): Set<String> {
        return com.example.coupleapp.data.health.HealthConnectManager.PERMISSIONS
    }
    
    /**
     * Sync sleep data from Health Connect to Firebase
     */
    suspend fun syncSleepDataFromHealthConnect(userId: String): Result<Int> {
        return try {
            val manager = healthConnectManager 
                ?: return Result.failure(Exception("Health Connect not initialized"))
            
            if (!manager.hasAllPermissions()) {
                return Result.failure(SecurityException("Missing Health Connect permissions"))
            }
            
            Log.d(TAG, "syncSleepDataFromHealthConnect: Starting sync for user=$userId")
            
            // Read last 7 days of sleep data
            val result = manager.readRecentSleepSessions(7)
            
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Failed to read Health Connect data"))
            }
            
            val sessions = result.getOrNull() ?: emptyList()
            Log.d(TAG, "syncSleepDataFromHealthConnect: Found ${sessions.size} sessions from Health Connect")
            
            var syncCount = 0
            
            // Sync each session to Firebase
            for (session in sessions) {
                val recordId = "${userId}_${session.date}"
                
                // Check if record already exists
                val existingDoc = firestore.collection(SLEEP_RECORDS_COLLECTION)
                    .document(recordId)
                    .get()
                    .await()
                
                // Only sync if doesn't exist or is older
                if (!existingDoc.exists()) {
                    val targetDuration = 480 // 8 hours default
                    val achievement = (session.totalSleepMinutes.toFloat() / targetDuration) * 100f
                    
                    val record = FirebaseSleepRecord(
                        id = recordId,
                        userId = userId,
                        coupleId = "",
                        date = Timestamp(Date.from(session.startTime)),
                        bedTimeHour = session.bedTime.hour,
                        bedTimeMinute = session.bedTime.minute,
                        wakeUpTimeHour = session.wakeUpTime.hour,
                        wakeUpTimeMinute = session.wakeUpTime.minute,
                        actualSleepDurationMinutes = session.totalSleepMinutes,
                        targetSleepDurationMinutes = targetDuration,
                        awakeDurationMinutes = session.awakeDurationMinutes,
                        sleepDurationMinutes = session.sleepDurationMinutes,
                        quality = when (session.quality) {
                            SleepQuality.EXCELLENT -> "EXCELLENT"
                            SleepQuality.GOOD -> "GOOD"
                            SleepQuality.POOR -> "POOR"
                        },
                        achievementPercentage = achievement.coerceIn(0f, 150f)
                    )
                    
                    firestore.collection(SLEEP_RECORDS_COLLECTION)
                        .document(recordId)
                        .set(record)
                        .await()
                    
                    syncCount++
                    Log.d(TAG, "syncSleepDataFromHealthConnect: Synced record for ${session.date}")
                }
            }
            
            Log.d(TAG, "syncSleepDataFromHealthConnect: Successfully synced $syncCount/${ sessions.size} records")
            Result.success(syncCount)
        } catch (e: Exception) {
            Log.e(TAG, "syncSleepDataFromHealthConnect: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get today's sleep data from Health Connect
     */
    suspend fun getTodaySleepFromHealthConnect(): Result<HealthSleepSession?> {
        return try {
            val manager = healthConnectManager 
                ?: return Result.failure(Exception("Health Connect not initialized"))
            
            if (!manager.hasAllPermissions()) {
                return Result.failure(SecurityException("Missing Health Connect permissions"))
            }
            
            manager.readTodaySleepSession()
        } catch (e: Exception) {
            Log.e(TAG, "getTodaySleepFromHealthConnect: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert HealthSleepSession to FirebaseSleepRecord
     */
    fun convertHealthSessionToFirebaseRecord(
        session: HealthSleepSession,
        userId: String,
        targetDuration: Int = 480
    ): FirebaseSleepRecord {
        val achievement = (session.totalSleepMinutes.toFloat() / targetDuration) * 100f
        
        return FirebaseSleepRecord(
            id = "${userId}_${session.date}",
            userId = userId,
            coupleId = "",
            date = Timestamp(Date.from(session.startTime)),
            bedTimeHour = session.bedTime.hour,
            bedTimeMinute = session.bedTime.minute,
            wakeUpTimeHour = session.wakeUpTime.hour,
            wakeUpTimeMinute = session.wakeUpTime.minute,
            actualSleepDurationMinutes = session.totalSleepMinutes,
            targetSleepDurationMinutes = targetDuration,
            awakeDurationMinutes = session.awakeDurationMinutes,
            sleepDurationMinutes = session.sleepDurationMinutes,
            quality = when (session.quality) {
                SleepQuality.EXCELLENT -> "EXCELLENT"
                SleepQuality.GOOD -> "GOOD"
                SleepQuality.POOR -> "POOR"
            },
            achievementPercentage = achievement.coerceIn(0f, 150f)
        )
    }
    
    /**
     * Auto-sync yesterday's sleep data from Health Connect
     * Should be called daily to keep data up to date
     */
    suspend fun autoSyncYesterdaySleepData(userId: String, healthManager: HealthConnectManager): Result<Boolean> {
        return try {
            if (!healthManager.hasAllPermissions()) {
                Log.w(TAG, "autoSyncYesterdaySleepData: Missing Health Connect permissions")
                return Result.success(false)
            }
            
            val yesterday = LocalDate.now().minusDays(1)
            val yesterdayId = "${userId}_${yesterday}"
            
            // Check if yesterday's data already exists
            val existingDoc = firestore.collection(SLEEP_RECORDS_COLLECTION)
                .document(yesterdayId)
                .get()
                .await()
            
            if (existingDoc.exists()) {
                Log.d(TAG, "autoSyncYesterdaySleepData: Yesterday's data already exists")
                return Result.success(true)
            }
            
            // Get yesterday's sleep data from Health Connect
            val sessionsResult = healthManager.readSleepSessions(yesterday, yesterday)
            
            if (sessionsResult.isFailure) {
                Log.e(TAG, "autoSyncYesterdaySleepData: Failed to read from Health Connect")
                return Result.success(false)
            }
            
            val sessions = sessionsResult.getOrNull() ?: emptyList()
            
            if (sessions.isEmpty()) {
                Log.d(TAG, "autoSyncYesterdaySleepData: No sleep data found for yesterday")
                return Result.success(false)
            }
            
            // Get user's target sleep duration
            val settings = getSleepSettings(userId).getOrNull()
            val targetDuration = settings?.targetSleepDurationMinutes ?: 480
            
            // Use the longest session of the day
            val mainSession = sessions.maxByOrNull { it.totalSleepMinutes }
            if (mainSession != null) {
                val record = convertHealthSessionToFirebaseRecord(mainSession, userId, targetDuration)
                
                firestore.collection(SLEEP_RECORDS_COLLECTION)
                    .document(yesterdayId)
                    .set(record)
                    .await()
                
                Log.d(TAG, "autoSyncYesterdaySleepData: Successfully synced yesterday's data")
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoSyncYesterdaySleepData: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if we should trigger auto-sync (once per day)
     * Returns true if last sync was more than 24 hours ago
     */
    suspend fun shouldAutoSync(userId: String): Boolean {
        return try {
            val settingsDoc = firestore.collection(SLEEP_SETTINGS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            val lastSyncTimestamp = settingsDoc.getTimestamp("lastAutoSync")
            
            if (lastSyncTimestamp == null) {
                return true // Never synced before
            }
            
            val lastSyncTime = lastSyncTimestamp.toDate().toInstant()
            val hoursSinceLastSync = java.time.Duration.between(lastSyncTime, Instant.now()).toHours()
            
            hoursSinceLastSync >= 24
        } catch (e: Exception) {
            Log.e(TAG, "shouldAutoSync: Error", e)
            true // Sync on error to be safe
        }
    }
    
    /**
     * Update last auto-sync timestamp
     */
    suspend fun updateLastAutoSyncTime(userId: String) {
        try {
            firestore.collection(SLEEP_SETTINGS_COLLECTION)
                .document(userId)
                .update("lastAutoSync", FieldValue.serverTimestamp())
                .await()
            
            Log.d(TAG, "updateLastAutoSyncTime: Updated for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "updateLastAutoSyncTime: Error", e)
        }
    }
}
