package com.example.coupleapp.widget.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.coupleapp.R
import com.example.coupleapp.widget.LocationWidgetProvider
import com.example.coupleapp.widget.LocketWidgetProvider
import com.example.coupleapp.widget.MissingWidgetProvider
import com.example.coupleapp.widget.SleepWidgetProvider
import com.example.coupleapp.widget.data.WidgetDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for battery-optimized widget updates
 * 
 * This follows modern Android patterns:
 * - Uses WorkManager for reliable background work
 * - Respects battery optimization (Doze mode, App Standby)
 * - Coalesces updates to minimize wake-ups
 * - Supports expedited work for critical updates
 */
class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val WORK_NAME_PERIODIC = "widget_periodic_update"
        private const val WORK_NAME_IMMEDIATE = "widget_immediate_update"
        
        private const val KEY_WIDGET_TYPE = "widget_type"
        private const val KEY_FORCE_REFRESH = "force_refresh"
        
        const val WIDGET_TYPE_ALL = "all"
        const val WIDGET_TYPE_SLEEP = "sleep"
        const val WIDGET_TYPE_LOCKET = "locket"
        const val WIDGET_TYPE_MISSING = "missing"
        const val WIDGET_TYPE_LOCATION = "location"
        
        // Notification ID and channel for foreground service (required for expedited work on Android 11-)
        private const val WIDGET_UPDATE_NOTIFICATION_ID = 10003
        private const val CHANNEL_ID_SYNC = "sync_channel"
        private const val CHANNEL_NAME_SYNC = "Background Sync"
        
        /**
         * Schedule periodic widget updates
         * This runs every 20 minutes (15 min for location), optimized for faster updates
         * Battery optimization: Uses PeriodicWorkRequest which respects Doze
         */
        fun schedulePeriodicUpdates(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true) // Don't run when battery is low
                .build()
            
            val periodicWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                20, TimeUnit.MINUTES,
                3, TimeUnit.MINUTES // Flex interval for battery optimization
            )
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_WIDGET_TYPE to WIDGET_TYPE_ALL,
                        KEY_FORCE_REFRESH to false
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .addTag("widget_update")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            
            Log.d(TAG, "Scheduled periodic widget updates every 20 minutes")
        }
        
        /**
         * Schedule faster updates for location widget (15 minutes)
         */
        fun scheduleLocationUpdates(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val locationWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES,
                2, TimeUnit.MINUTES // Smaller flex interval
            )
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_WIDGET_TYPE to WIDGET_TYPE_LOCATION,
                        KEY_FORCE_REFRESH to false
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    5, TimeUnit.MINUTES
                )
                .addTag("location_update")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${WORK_NAME_PERIODIC}_location",
                ExistingPeriodicWorkPolicy.KEEP,
                locationWorkRequest
            )
            
            Log.d(TAG, "Scheduled location widget updates every 15 minutes")
        }
        
        /**
         * Cancel all scheduled updates
         * Call this when user logs out or disables widgets
         */
        fun cancelPeriodicUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork("${WORK_NAME_PERIODIC}_location")
            Log.d(TAG, "Cancelled all periodic widget updates")
        }
        
        /**
         * Request immediate widget update
         * Uses expedited work for time-sensitive updates
         */
        fun requestImmediateUpdate(context: Context, widgetType: String = WIDGET_TYPE_ALL) {
            val immediateWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    workDataOf(
                        KEY_WIDGET_TYPE to widgetType,
                        KEY_FORCE_REFRESH to true
                    )
                )
                .addTag("widget_immediate")
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_IMMEDIATE}_$widgetType",
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )
            
            Log.d(TAG, "Requested immediate update for widget type: $widgetType")
        }
        
        /**
         * Schedule bedtime reminder update
         * Runs at specific time before user's bedtime
         */
        fun scheduleBedtimeUpdate(context: Context, delayMinutes: Long) {
            val bedtimeWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_WIDGET_TYPE to WIDGET_TYPE_SLEEP,
                        KEY_FORCE_REFRESH to true
                    )
                )
                .addTag("bedtime_reminder")
                .build()
            
            WorkManager.getInstance(context).enqueue(bedtimeWorkRequest)
            Log.d(TAG, "Scheduled bedtime update in $delayMinutes minutes")
        }
    }
    
    /**
     * Required for expedited work on Android 11 (API 30) and below.
     * On Android 12+, expedited work uses Android 12's expedited job feature.
     * On older versions, WorkManager runs the work as a foreground service.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_SYNC)
            .setSmallIcon(R.drawable.ic_heart_notification)
            .setContentTitle("Đang cập nhật widget")
            .setContentText("Đang làm mới dữ liệu widget...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        return ForegroundInfo(WIDGET_UPDATE_NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SYNC,
                CHANNEL_NAME_SYNC,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị khi đang cập nhật widget"
                setShowBadge(false)
            }
            
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val widgetType = inputData.getString(KEY_WIDGET_TYPE) ?: WIDGET_TYPE_ALL
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        
        Log.d(TAG, "Starting widget update work: type=$widgetType, forceRefresh=$forceRefresh")
        
        return try {
            withContext(Dispatchers.IO) {
                when (widgetType) {
                    WIDGET_TYPE_ALL -> updateAllWidgets(forceRefresh)
                    WIDGET_TYPE_SLEEP -> updateSleepWidget(forceRefresh)
                    WIDGET_TYPE_LOCKET -> updateLocketWidget(forceRefresh)
                    WIDGET_TYPE_MISSING -> updateMissingWidget(forceRefresh)
                    WIDGET_TYPE_LOCATION -> updateLocationWidget(forceRefresh)
                    else -> updateAllWidgets(forceRefresh)
                }
            }
            
            Log.d(TAG, "Widget update completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun updateAllWidgets(forceRefresh: Boolean) {
        updateSleepWidget(forceRefresh)
        updateLocketWidget(forceRefresh)
        updateMissingWidget(forceRefresh)
        updateLocationWidget(forceRefresh)
    }
    
    private suspend fun updateSleepWidget(forceRefresh: Boolean) {
        try {
            // Pre-fetch data to warm up cache
            WidgetDataRepository.getSleepWidgetData(context, forceRefresh)
            
            // Trigger widget UI update
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SleepWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                SleepWidgetProvider.updateWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sleep widget", e)
        }
    }
    
    private suspend fun updateLocketWidget(forceRefresh: Boolean) {
        try {
            WidgetDataRepository.getLocketWidgetData(context, forceRefresh)
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, LocketWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                LocketWidgetProvider.updateWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update locket widget", e)
        }
    }
    
    private suspend fun updateMissingWidget(forceRefresh: Boolean) {
        try {
            WidgetDataRepository.getMissingWidgetData(context, forceRefresh)
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MissingWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                MissingWidgetProvider.updateWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update missing widget", e)
        }
    }
    
    private suspend fun updateLocationWidget(forceRefresh: Boolean) {
        try {
            WidgetDataRepository.getLocationWidgetData(context, forceRefresh)
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, LocationWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                LocationWidgetProvider.updateWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update location widget", e)
        }
    }
}
