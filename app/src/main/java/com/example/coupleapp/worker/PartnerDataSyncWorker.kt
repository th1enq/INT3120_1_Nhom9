package com.example.coupleapp.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.example.coupleapp.data.sync.PartnerSyncRepository
import com.example.coupleapp.widget.LocationWidgetProvider
import com.example.coupleapp.widget.LocketWidgetProvider
import com.example.coupleapp.widget.MissingWidgetProvider
import com.example.coupleapp.widget.SleepWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * CoroutineWorker responsible for syncing partner data from Firebase to Room DB
 * and triggering widget updates.
 * 
 * This worker implements the "Silent Push" strategy:
 * 1. Triggered by FCM data message (via SilentPushFCMService)
 * 2. Fetches latest data from Firebase/Backend
 * 3. Saves data to Room Database (single source of truth)
 * 4. Triggers widget refresh via AppWidgetManager
 * 
 * Battery Optimization Features:
 * - Supports expedited work for immediate updates
 * - Respects Doze mode through WorkManager
 * - Uses exponential backoff for retries
 * - Coalesces multiple sync requests
 * 
 * Doze Mode Handling:
 * - When device is in Doze, WorkManager defers execution
 * - High-priority FCM messages can wake device temporarily
 * - Expedited work gets priority in maintenance windows
 */
class PartnerDataSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "PartnerDataSyncWorker"
        
        // Work names
        private const val WORK_NAME_SYNC = "partner_data_sync"
        private const val WORK_NAME_PERIODIC = "partner_data_periodic_sync"
        
        // Input data keys
        const val KEY_PARTNER_ID = "partner_id"
        const val KEY_SYNC_TYPES = "sync_types"
        const val KEY_TRIGGER_SOURCE = "trigger_source"
        const val KEY_IS_EXPEDITED = "is_expedited"
        
        // Trigger sources for debugging/analytics
        const val TRIGGER_FCM = "fcm"
        const val TRIGGER_MANUAL = "manual"
        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_APP_OPEN = "app_open"
        
        // Sync types (comma-separated in input data)
        const val SYNC_TYPE_ALL = "all"
        const val SYNC_TYPE_SLEEP = "sleep"
        const val SYNC_TYPE_LOCATION = "location"
        const val SYNC_TYPE_PHOTOS = "photos"
        
        // Widget update actions
        private const val ACTION_UPDATE_SLEEP_WIDGET = "com.example.coupleapp.widget.UPDATE_SLEEP"
        private const val ACTION_UPDATE_LOCKET_WIDGET = "com.example.coupleapp.widget.UPDATE_LOCKET"
        private const val ACTION_UPDATE_LOCATION_WIDGET = "com.example.coupleapp.widget.UPDATE_LOCATION"
        
        // Notification ID for foreground service
        private const val SYNC_NOTIFICATION_ID = 10001
        
        /**
         * Enqueue an expedited sync request.
         * Used when FCM high-priority message arrives for immediate widget updates.
         * 
         * Expedited work characteristics:
         * - Starts immediately even if app is in background
         * - Gets execution quota (limited time window)
         * - Falls back to regular work if quota exceeded
         */
        fun enqueueExpedited(
            context: Context,
            partnerId: String,
            syncTypes: List<String>? = null,
            triggerSource: String = TRIGGER_FCM
        ) {
            Log.d(TAG, "Enqueuing expedited sync: partner=$partnerId, types=$syncTypes, trigger=$triggerSource")
            
            val inputData = workDataOf(
                KEY_PARTNER_ID to partnerId,
                KEY_SYNC_TYPES to (syncTypes?.joinToString(",") ?: SYNC_TYPE_ALL),
                KEY_TRIGGER_SOURCE to triggerSource,
                KEY_IS_EXPEDITED to true
            )
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<PartnerDataSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("sync_expedited")
                .addTag("partner_$partnerId")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            // Use REPLACE to avoid duplicate syncs
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_SYNC}_${partnerId}_expedited",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
        
        /**
         * Enqueue a regular (non-expedited) sync request.
         * Used for manual refresh or when user opens app.
         */
        fun enqueueRegular(
            context: Context,
            partnerId: String,
            syncTypes: List<String>? = null,
            triggerSource: String = TRIGGER_MANUAL
        ) {
            Log.d(TAG, "Enqueuing regular sync: partner=$partnerId, types=$syncTypes")
            
            val inputData = workDataOf(
                KEY_PARTNER_ID to partnerId,
                KEY_SYNC_TYPES to (syncTypes?.joinToString(",") ?: SYNC_TYPE_ALL),
                KEY_TRIGGER_SOURCE to triggerSource,
                KEY_IS_EXPEDITED to false
            )
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true) // Don't drain battery unnecessarily
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<PartnerDataSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("sync_regular")
                .addTag("partner_$partnerId")
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_SYNC}_${partnerId}_regular",
                ExistingWorkPolicy.KEEP, // Keep existing to avoid redundant syncs
                workRequest
            )
        }
        
        /**
         * Schedule periodic background sync.
         * Runs every 30 minutes to keep widget data fresh.
         * 
         * This is a fallback mechanism when FCM might be delayed or blocked.
         */
        fun schedulePeriodicSync(context: Context, partnerId: String) {
            Log.d(TAG, "Scheduling periodic sync for partner: $partnerId")
            
            val inputData = workDataOf(
                KEY_PARTNER_ID to partnerId,
                KEY_SYNC_TYPES to SYNC_TYPE_ALL,
                KEY_TRIGGER_SOURCE to TRIGGER_PERIODIC,
                KEY_IS_EXPEDITED to false
            )
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val periodicRequest = PeriodicWorkRequestBuilder<PartnerDataSyncWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES // Flex interval for battery optimization
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("sync_periodic")
                .addTag("partner_$partnerId")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${WORK_NAME_PERIODIC}_$partnerId",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }
        
        /**
         * Cancel periodic sync (e.g., on logout).
         */
        fun cancelPeriodicSync(context: Context, partnerId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("${WORK_NAME_PERIODIC}_$partnerId")
            Log.d(TAG, "Cancelled periodic sync for partner: $partnerId")
        }
        
        /**
         * Cancel all sync work for a partner.
         */
        fun cancelAllSync(context: Context, partnerId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag("partner_$partnerId")
            Log.d(TAG, "Cancelled all sync work for partner: $partnerId")
        }
    }
    
    private val syncRepository = PartnerSyncRepository.getInstance(context)
    
    override suspend fun doWork(): Result {
        val partnerId = inputData.getString(KEY_PARTNER_ID)
        val syncTypesStr = inputData.getString(KEY_SYNC_TYPES) ?: SYNC_TYPE_ALL
        val triggerSource = inputData.getString(KEY_TRIGGER_SOURCE) ?: TRIGGER_MANUAL
        val isExpedited = inputData.getBoolean(KEY_IS_EXPEDITED, false)
        
        Log.d(TAG, "Starting sync work: partner=$partnerId, types=$syncTypesStr, " +
                "trigger=$triggerSource, expedited=$isExpedited, attempt=${runAttemptCount + 1}")
        
        // Validate partner ID
        if (partnerId.isNullOrEmpty()) {
            Log.e(TAG, "Partner ID is missing, attempting to fetch from repository")
            val fetchedPartnerId = syncRepository.getPartnerId()
            if (fetchedPartnerId == null) {
                Log.e(TAG, "Could not determine partner ID, aborting")
                return Result.failure()
            }
            return executeSyncWithPartnerId(fetchedPartnerId, syncTypesStr)
        }
        
        return executeSyncWithPartnerId(partnerId, syncTypesStr)
    }
    
    private suspend fun executeSyncWithPartnerId(partnerId: String, syncTypesStr: String): Result {
        return try {
            // Parse sync types
            val syncTypes = if (syncTypesStr == SYNC_TYPE_ALL) {
                listOf(
                    PartnerSyncRepository.DATA_TYPE_SLEEP,
                    PartnerSyncRepository.DATA_TYPE_LOCATION,
                    PartnerSyncRepository.DATA_TYPE_PHOTOS
                )
            } else {
                syncTypesStr.split(",").mapNotNull { type ->
                    when (type.trim()) {
                        SYNC_TYPE_SLEEP -> PartnerSyncRepository.DATA_TYPE_SLEEP
                        SYNC_TYPE_LOCATION -> PartnerSyncRepository.DATA_TYPE_LOCATION
                        SYNC_TYPE_PHOTOS -> PartnerSyncRepository.DATA_TYPE_PHOTOS
                        else -> null
                    }
                }
            }
            
            // Perform sync
            val syncResult = syncRepository.syncPartnerData(partnerId, syncTypes)
            
            Log.d(TAG, "Sync completed: success=${syncResult.success}, " +
                    "synced=${syncResult.syncedTypes}, failed=${syncResult.failedTypes}")
            
            // Trigger widget updates for successfully synced types
            if (syncResult.syncedTypes.isNotEmpty()) {
                triggerWidgetUpdates(syncResult.syncedTypes)
            }
            
            // Clean up old data periodically
            syncRepository.cleanupOldData()
            
            if (syncResult.success) {
                Result.success()
            } else if (syncResult.syncedTypes.isNotEmpty()) {
                // Partial success - some types synced
                Result.success()
            } else if (runAttemptCount < 3) {
                // All failed - retry with backoff
                Log.w(TAG, "Sync failed, will retry (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                Log.e(TAG, "Sync failed after max retries")
                Result.failure()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed with exception", e)
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * Trigger widget updates after data sync.
     * Uses both broadcast (for receivers) and direct AppWidgetManager calls.
     */
    private suspend fun triggerWidgetUpdates(syncedTypes: List<String>) {
        withContext(Dispatchers.Main) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            for (syncType in syncedTypes) {
                when (syncType) {
                    PartnerSyncRepository.DATA_TYPE_SLEEP -> {
                        Log.d(TAG, "Triggering Sleep widget update")
                        // Broadcast approach
                        context.sendBroadcast(Intent(ACTION_UPDATE_SLEEP_WIDGET).apply {
                            setPackage(context.packageName)
                        })
                        // Direct update
                        val sleepWidgetIds = appWidgetManager.getAppWidgetIds(
                            ComponentName(context, SleepWidgetProvider::class.java)
                        )
                        if (sleepWidgetIds.isNotEmpty()) {
                            SleepWidgetProvider.updateWidgets(context)
                        }
                    }
                    
                    PartnerSyncRepository.DATA_TYPE_LOCATION -> {
                        Log.d(TAG, "Triggering Location widget update")
                        context.sendBroadcast(Intent(ACTION_UPDATE_LOCATION_WIDGET).apply {
                            setPackage(context.packageName)
                        })
                        val locationWidgetIds = appWidgetManager.getAppWidgetIds(
                            ComponentName(context, LocationWidgetProvider::class.java)
                        )
                        if (locationWidgetIds.isNotEmpty()) {
                            LocationWidgetProvider.updateWidgets(context)
                        }
                    }
                    
                    PartnerSyncRepository.DATA_TYPE_PHOTOS -> {
                        Log.d(TAG, "Triggering Locket widget update")
                        context.sendBroadcast(Intent(ACTION_UPDATE_LOCKET_WIDGET).apply {
                            setPackage(context.packageName)
                        })
                        val locketWidgetIds = appWidgetManager.getAppWidgetIds(
                            ComponentName(context, LocketWidgetProvider::class.java)
                        )
                        if (locketWidgetIds.isNotEmpty()) {
                            LocketWidgetProvider.forceUpdateWidgets(context)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Override to provide foreground notification for expedited work.
     * Required for Android 12+ expedited work.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }
    
    private fun createForegroundInfo(): ForegroundInfo {
        val notification = androidx.core.app.NotificationCompat.Builder(
            context,
            "sync_channel"
        )
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Syncing Partner Data")
            .setContentText("Updating widget information...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        return ForegroundInfo(
            SYNC_NOTIFICATION_ID,
            notification
        )
    }
}
