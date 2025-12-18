package com.example.coupleapp.widget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.*
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
        
        /**
         * Schedule periodic widget updates
         * This runs every 30 minutes, matching the widget update interval
         * Battery optimization: Uses PeriodicWorkRequest which respects Doze
         */
        fun schedulePeriodicUpdates(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true) // Don't run when battery is low
                .build()
            
            val periodicWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                30, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval for battery optimization
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
            
            Log.d(TAG, "Scheduled periodic widget updates")
        }
        
        /**
         * Cancel all scheduled updates
         * Call this when user logs out or disables widgets
         */
        fun cancelPeriodicUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            Log.d(TAG, "Cancelled periodic widget updates")
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
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        // For expedited work on Android 12+
        val notification = androidx.core.app.NotificationCompat.Builder(context, "widget_update_channel")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Đang cập nhật widget...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
        
        return ForegroundInfo(9999, notification)
    }
}
