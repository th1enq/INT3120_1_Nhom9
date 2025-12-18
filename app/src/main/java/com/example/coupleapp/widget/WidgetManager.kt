package com.example.coupleapp.widget

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Centralized manager for all app widgets
 * Provides battery-optimized update strategies
 */
object WidgetManager {
    private const val TAG = "WidgetManager"
    
    /**
     * Update all widgets of all types
     * Should be called sparingly to save battery
     */
    fun updateAllWidgets(context: Context) {
        Log.d(TAG, "Updating all widgets")
        SleepWidgetManager.updateAllWidgets(context)
        LocketWidgetProvider.updateWidgets(context)
        MissingWidgetProvider.updateWidgets(context)
        LocationWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Update Locket widget when new Locket is received or sent
     */
    fun onLocketUpdated(context: Context) {
        Log.d(TAG, "Locket data changed, updating widget")
        CoroutineScope(Dispatchers.Main).launch {
            LocketWidgetProvider.updateWidgets(context)
        }
    }
    
    /**
     * Update Missing widget when user sends or receives missing
     */
    fun onMissingUpdated(context: Context) {
        Log.d(TAG, "Missing data changed, updating widget")
        CoroutineScope(Dispatchers.Main).launch {
            MissingWidgetProvider.updateWidgets(context)
        }
    }
    
    /**
     * Update Location widget when location data changes
     * This is called by LocationTrackingService
     */
    fun onLocationUpdated(context: Context) {
        Log.d(TAG, "Location data changed, updating widget")
        CoroutineScope(Dispatchers.Main).launch {
            LocationWidgetProvider.updateWidgets(context)
        }
    }
    
    /**
     * Update Sleep widget when sleep data changes
     * This is called by SleepTrackerViewModel
     */
    fun onSleepDataUpdated(context: Context) {
        Log.d(TAG, "Sleep data changed, updating widget")
        CoroutineScope(Dispatchers.Main).launch {
            SleepWidgetManager.onSleepDataChanged(context)
        }
    }
}

/**
 * Helper object for Locket widget management
 */
object LocketWidgetManager {
    /**
     * Update all Locket widgets with latest data
     */
    fun updateAllWidgets(context: Context) {
        LocketWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Notify widget when new Locket is sent
     */
    fun onLocketSent(context: Context) {
        updateAllWidgets(context)
    }
    
    /**
     * Notify widget when new Locket is received
     */
    fun onLocketReceived(context: Context) {
        updateAllWidgets(context)
    }
}

/**
 * Helper object for Missing widget management
 */
object MissingWidgetManager {
    /**
     * Update all Missing widgets with latest data
     */
    fun updateAllWidgets(context: Context) {
        MissingWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Notify widget when missing is sent
     */
    fun onMissingSent(context: Context) {
        updateAllWidgets(context)
    }
}

/**
 * Helper object for Location widget management
 */
object LocationWidgetManager {
    /**
     * Update all Location widgets with latest data
     */
    fun updateAllWidgets(context: Context) {
        LocationWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Notify widget when location is updated
     */
    fun onLocationChanged(context: Context) {
        updateAllWidgets(context)
    }
    
    /**
     * Notify widget when location sharing status changes
     */
    fun onSharingStatusChanged(context: Context) {
        updateAllWidgets(context)
    }
}
