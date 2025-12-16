package com.example.coupleapp.widget

import android.content.Context
import android.content.Intent
import com.example.coupleapp.data.repository.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Helper class to manage widget updates
 */
object SleepWidgetManager {
    
    /**
     * Update all widgets with latest sleep data
     */
    fun updateAllWidgets(context: Context) {
        SleepWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Schedule periodic widget updates
     * Should be called when sleep data changes or at bedtime
     */
    fun scheduleWidgetUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get current user's bedtime settings
                val currentUser = SleepRepository.getCurrentUser()
                val settings = SleepRepository.getSleepSettings(currentUser.id)
                
                // Check if it's bedtime soon (within 15 minutes before or 30 minutes after)
                val now = LocalTime.now()
                val reminderStart = settings.idealBedTime.minusMinutes(15)
                val reminderEnd = settings.idealBedTime.plusMinutes(30)
                
                // Update widget immediately
                updateAllWidgets(context)
                
                // If within bedtime window, schedule more frequent updates
                if (now.isAfter(reminderStart) && now.isBefore(reminderEnd)) {
                    // Widget will show bedtime reminder
                    // System will handle the update interval defined in widget_info.xml
                }
                
            } catch (e: Exception) {
                // Fallback: just update widget
                updateAllWidgets(context)
            }
        }
    }
    
    /**
     * Notify widget when sleep data is updated
     */
    fun onSleepDataChanged(context: Context) {
        updateAllWidgets(context)
    }
    
    /**
     * Notify widget when bedtime settings changed
     */
    fun onBedtimeSettingsChanged(context: Context) {
        scheduleWidgetUpdate(context)
    }
}
