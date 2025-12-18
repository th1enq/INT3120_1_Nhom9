package com.example.coupleapp.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.coupleapp.data.health.HealthConnectManager
import com.example.coupleapp.data.repository.SleepFirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Background worker for syncing sleep data from Health Connect.
 * This ensures accurate sleep tracking even when the app is not open.
 * 
 * The worker runs:
 * 1. Periodically every 4 hours to catch up on sleep data
 * 2. Specifically in the morning (6 AM - 12 PM) to sync yesterday's sleep
 * 
 * Benefits:
 * - Accurate wake-up time detection (not when app opens)
 * - Battery efficient (uses WorkManager constraints)
 * - Works even if user doesn't open app in the morning
 */
class SleepSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "SleepSyncWorker"
        const val WORK_NAME = "sleep_sync_worker"
        const val MORNING_SYNC_WORK_NAME = "morning_sleep_sync_worker"
        
        /**
         * Schedule periodic sleep sync (every 4 hours)
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SleepSyncWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            
            Log.d(TAG, "Scheduled periodic sleep sync every 4 hours")
        }
        
        /**
         * Schedule morning sync specifically (runs between 6 AM - 12 PM)
         * This is the most important sync to catch yesterday's sleep data
         */
        fun scheduleMorningSync(context: Context) {
            val now = LocalTime.now()
            val morningStart = LocalTime.of(6, 0)
            val morningEnd = LocalTime.of(12, 0)
            
            // Calculate delay to next morning window
            val delayMinutes = when {
                now.isBefore(morningStart) -> {
                    java.time.Duration.between(now, morningStart).toMinutes()
                }
                now.isBefore(morningEnd) -> {
                    0L // Already in morning window
                }
                else -> {
                    // Next morning
                    java.time.Duration.between(now, LocalTime.MAX).toMinutes() +
                    java.time.Duration.between(LocalTime.MIN, morningStart).toMinutes()
                }
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SleepSyncWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(MORNING_SYNC_WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                MORNING_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            
            Log.d(TAG, "Scheduled morning sleep sync with $delayMinutes minutes delay")
        }
        
        /**
         * Trigger immediate sync (e.g., when app opens)
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SleepSyncWorker>()
                .setConstraints(constraints)
                .addTag("immediate_sleep_sync")
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            Log.d(TAG, "Triggered immediate sleep sync")
        }
        
        /**
         * Cancel all sleep sync workers
         */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
            WorkManager.getInstance(context).cancelAllWorkByTag(MORNING_SYNC_WORK_NAME)
            Log.d(TAG, "Cancelled all sleep sync workers")
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sleep sync work")
            
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid
            
            if (userId == null) {
                Log.w(TAG, "No authenticated user, skipping sync")
                return@withContext Result.success()
            }
            
            val healthManager = HealthConnectManager(context)
            val repository = SleepFirebaseRepository(context)
            
            // Check if Health Connect is available and has permissions
            if (!healthManager.isAvailable()) {
                Log.w(TAG, "Health Connect not available, skipping sync")
                return@withContext Result.success()
            }
            
            if (!healthManager.hasAllPermissions()) {
                Log.w(TAG, "Missing Health Connect permissions, skipping sync")
                return@withContext Result.success()
            }
            
            // Sync yesterday's and today's sleep data
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            Log.d(TAG, "Syncing sleep data for $yesterday and $today")
            
            // Get sleep sessions from Health Connect
            val sessionsResult = healthManager.readSleepSessions(yesterday, today)
            
            if (sessionsResult.isFailure) {
                Log.e(TAG, "Failed to read sleep sessions from Health Connect")
                return@withContext Result.retry()
            }
            
            val sessions = sessionsResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Found ${sessions.size} sleep sessions from Health Connect")
            
            if (sessions.isEmpty()) {
                Log.d(TAG, "No sleep sessions found")
                return@withContext Result.success()
            }
            
            // Get user's target sleep duration
            val settings = repository.getSleepSettings(userId).getOrNull()
            val targetDuration = settings?.targetSleepDurationMinutes ?: 480
            
            // Group sessions by date and use the longest session for each day
            val sessionsByDate = sessions.groupBy { it.date }
            
            for ((date, dateSessions) in sessionsByDate) {
                val mainSession = dateSessions.maxByOrNull { it.totalSleepMinutes } ?: continue
                
                val recordId = "${userId}_${date}"
                
                // Check if record already exists (don't overwrite manual tracking)
                val existingRecordResult = repository.getSleepHistory(userId, 7)
                val existingRecords = existingRecordResult.getOrNull() ?: emptyList()
                val existingRecord = existingRecords.find { it.id == recordId }
                
                // Skip if record exists and was manually tracked
                if (existingRecord != null && existingRecord.trackingMethod == "MANUAL") {
                    Log.d(TAG, "Skipping $date - manual record exists")
                    continue
                }
                
                // Convert session to Firebase record
                val record = repository.convertHealthSessionToFirebaseRecord(
                    session = mainSession,
                    userId = userId,
                    targetDuration = targetDuration
                )
                
                // Save to Firebase
                val saveResult = repository.saveSleepRecord(record.copy(id = recordId))
                
                if (saveResult.isSuccess) {
                    Log.d(TAG, "Saved sleep record for $date: ${mainSession.totalSleepMinutes} minutes, bed=${mainSession.bedTime}, wake=${mainSession.wakeUpTime}")
                } else {
                    Log.w(TAG, "Failed to save sleep record for $date")
                }
            }
            
            // Update last sync time
            repository.updateLastAutoSyncTime(userId)
            
            // Schedule next morning sync
            scheduleMorningSync(context)
            
            Log.d(TAG, "Sleep sync completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during sleep sync", e)
            Result.retry()
        }
    }
}
