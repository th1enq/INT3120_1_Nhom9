package com.example.coupleapp.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.example.coupleapp.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Helper class to manage widget updates
 * 
 * Battery Optimization:
 * - Uses cached data from WidgetDataRepository
 * - Schedules bedtime reminders via WorkManager
 * - Smart update scheduling based on user's bedtime
 */
object SleepWidgetManager {
    private const val TAG = "SleepWidgetManager"
    
    /**
     * Update all widgets with latest sleep data from cache
     */
    fun updateAllWidgets(context: Context) {
        Log.d(TAG, "Updating sleep widgets from cache")
        SleepWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Force update with fresh data from Firebase
     */
    fun forceUpdateAllWidgets(context: Context) {
        Log.d(TAG, "Force updating sleep widgets")
        SleepWidgetProvider.forceUpdateWidgets(context)
    }
    
    /**
     * Schedule widget update and bedtime reminder
     * Should be called when sleep settings change or app starts
     */
    fun scheduleWidgetUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get cached sleep data to determine bedtime
                val sleepData = WidgetDataRepository.getSleepWidgetData(context)
                
                if (sleepData != null) {
                    val now = LocalTime.now()
                    val bedtime = LocalTime.of(sleepData.bedtimeHour, sleepData.bedtimeMinute)
                    
                    // Calculate minutes until bedtime
                    val minutesUntilBedtime = now.until(bedtime, ChronoUnit.MINUTES)
                    
                    // If bedtime is within next 2 hours, schedule reminder
                    if (minutesUntilBedtime in 1..120) {
                        // Schedule reminder 15 minutes before bedtime
                        val reminderMinutes = minutesUntilBedtime - 15
                        if (reminderMinutes > 0) {
                            WidgetUpdateWorker.scheduleBedtimeUpdate(context, reminderMinutes)
                            Log.d(TAG, "Scheduled bedtime reminder in $reminderMinutes minutes")
                        }
                    }
                }
                
                // Update widget immediately
                updateAllWidgets(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling widget update", e)
                // Fallback: just update widget
                updateAllWidgets(context)
            }
        }
    }
    
    /**
     * Notify widget when sleep data is updated
     * Invalidates cache and triggers refresh
     */
    fun onSleepDataChanged(context: Context) {
        Log.d(TAG, "Sleep data changed")
        WidgetDataRepository.invalidateSleepCache(context)
        updateAllWidgets(context)
    }
    
    /**
     * Notify widget when user wakes up
     * Updates widget to show today's sleep summary
     */
    fun onWakeUp(context: Context) {
        Log.d(TAG, "User woke up, updating widget")
        WidgetDataRepository.invalidateSleepCache(context)
        forceUpdateAllWidgets(context)
    }
    
    /**
     * Notify widget when user goes to sleep
     * Updates partner's widget to show sleeping status
     */
    fun onSleepStart(context: Context) {
        Log.d(TAG, "User started sleeping")
        WidgetDataRepository.invalidateSleepCache(context)
        updateAllWidgets(context)
    }
    
    /**
     * Notify widget when bedtime settings changed
     * Reschedules bedtime reminder
     */
    fun onBedtimeSettingsChanged(context: Context) {
        Log.d(TAG, "Bedtime settings changed")
        WidgetDataRepository.invalidateSleepCache(context)
        scheduleWidgetUpdate(context)
    }
}
