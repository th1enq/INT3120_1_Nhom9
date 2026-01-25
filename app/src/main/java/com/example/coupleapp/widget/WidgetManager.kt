package com.example.coupleapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.coupleapp.widget.cache.WidgetImageCache
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.example.coupleapp.widget.observer.WidgetFirestoreObserver
import com.example.coupleapp.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Centralized manager for all app widgets
 * 
 * Battery & Data Optimization Strategies:
 * 1. Aggressive Caching - WidgetDataRepository handles SharedPreferences caching
 * 2. Image Cache - WidgetImageCache stores decoded bitmaps on disk
 * 3. Widget Thumbnails - Small versions of images for efficient widget sync
 * 4. WorkManager Integration - Respects Doze mode and App Standby
 * 5. Smart Invalidation - Only refresh when data actually changes
 * 6. Coalesced Updates - Batch multiple update requests
 * 7. Expedited Work - For time-sensitive updates (new Locket, etc.)
 * 
 * Update Intervals:
 * - Sleep: 30 minutes (matches bedtime cycle)
 * - Locket: 5 minutes cache (real-time feel for new messages)
 * - Missing: 15 minutes (streak data doesn't change often)
 * - Location: 10 minutes (balance between accuracy and battery)
 */
object WidgetManager {
    private const val TAG = "WidgetManager"
    
    /**
     * Initialize widget system
     * Should be called in Application.onCreate() or MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing widget system")
        
        // Check if any widgets are active
        val hasActiveWidgets = hasAnyActiveWidgets(context)
        
        if (hasActiveWidgets) {
            // Schedule periodic updates if widgets exist
            WidgetUpdateWorker.schedulePeriodicUpdates(context)
            Log.d(TAG, "Periodic widget updates scheduled")
            
            // Start Firestore realtime observer for immediate widget updates
            WidgetFirestoreObserver.startObserving(context)
            Log.d(TAG, "Firestore observer started for realtime updates")
        }
    }
    
    /**
     * Check if there are any active widgets of any type
     */
    fun hasAnyActiveWidgets(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        val sleepWidgets = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SleepWidgetProvider::class.java)
        )
        val locketWidgets = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LocketWidgetProvider::class.java)
        )
        val missingWidgets = appWidgetManager.getAppWidgetIds(
            ComponentName(context, MissingWidgetProvider::class.java)
        )
        val locationWidgets = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LocationWidgetProvider::class.java)
        )
        
        return sleepWidgets.isNotEmpty() || locketWidgets.isNotEmpty() || 
               missingWidgets.isNotEmpty() || locationWidgets.isNotEmpty()
    }
    
    /**
     * Update all widgets of all types using cached data
     * This is battery-efficient as it uses cached data
     */
    fun updateAllWidgets(context: Context) {
        Log.d(TAG, "Updating all widgets from cache")
        SleepWidgetProvider.updateWidgets(context)
        LocketWidgetProvider.updateWidgets(context)
        MissingWidgetProvider.updateWidgets(context)
        LocationWidgetProvider.updateWidgets(context)
    }
    
    /**
     * Force refresh all widgets with fresh data from Firebase
     * Use sparingly - this bypasses cache
     */
    fun forceRefreshAllWidgets(context: Context) {
        Log.d(TAG, "Force refreshing all widgets")
        WidgetUpdateWorker.requestImmediateUpdate(context, WidgetUpdateWorker.WIDGET_TYPE_ALL)
    }
    
    /**
     * Update Locket widget when new Locket is received or sent
     * Invalidates cache to show fresh data immediately
     */
    fun onLocketUpdated(context: Context) {
        Log.d(TAG, "Locket data changed, updating widget")
        // Use GlobalScope for widget updates since widgets don't have a lifecycle
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main.immediate) {
            LocketWidgetProvider.onNewLocketReceived(context)
        }
    }
    
    /**
     * Update Missing widget when user sends or receives missing
     * Uses immediate cache update for responsive UI
     */
    fun onMissingUpdated(context: Context) {
        Log.d(TAG, "Missing data changed, updating widget")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main.immediate) {
            MissingWidgetProvider.onMissingReceived(context)
        }
    }
    
    /**
     * Update Location widget when location data changes
     * Called by LocationTrackingService when significant location change detected
     */
    fun onLocationUpdated(context: Context) {
        Log.d(TAG, "Location data changed, updating widget")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main.immediate) {
            LocationWidgetProvider.onLocationChanged(context)
        }
    }
    
    /**
     * Update Sleep widget when sleep data changes
     * Called by SleepTrackerViewModel or SleepReceiver
     */
    fun onSleepDataUpdated(context: Context) {
        Log.d(TAG, "Sleep data changed, updating widget")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main.immediate) {
            WidgetDataRepository.invalidateSleepCache(context)
            SleepWidgetProvider.updateWidgets(context)
        }
    }
    
    /**
     * Schedule bedtime reminder update
     * Called when user's bedtime is approaching
     */
    fun scheduleBedtimeReminder(context: Context, minutesUntilBedtime: Long) {
        Log.d(TAG, "Scheduling bedtime reminder in $minutesUntilBedtime minutes")
        WidgetUpdateWorker.scheduleBedtimeUpdate(context, minutesUntilBedtime)
    }
    
    /**
     * Clear all cached widget data
     * Call when user logs out or switches accounts
     */
    fun clearAllWidgetData(context: Context) {
        Log.d(TAG, "Clearing all widget cache data")
        WidgetDataRepository.clearCache(context)
        WidgetImageCache.clearAll(context) // Clear image cache too
        WidgetUpdateWorker.cancelPeriodicUpdates(context)
        WidgetFirestoreObserver.stopObserving()
        updateAllWidgets(context)
    }
    
    /**
     * Handle user login - reinitialize widgets with fresh data
     */
    fun onUserLoggedIn(context: Context) {
        Log.d(TAG, "User logged in, refreshing widgets")
        initialize(context)
        forceRefreshAllWidgets(context)
    }
    
    /**
     * Handle user logout - clear cache and show empty state
     */
    fun onUserLoggedOut(context: Context) {
        Log.d(TAG, "User logged out, clearing widgets")
        WidgetFirestoreObserver.stopObserving()
        clearAllWidgetData(context)
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
        LocketWidgetProvider.onNewLocketReceived(context)
    }
    
    /**
     * Notify widget when new Locket is received
     */
    fun onLocketReceived(context: Context) {
        LocketWidgetProvider.onNewLocketReceived(context)
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
        MissingWidgetProvider.onMissingReceived(context)
    }
    
    /**
     * Notify widget when missing is received from partner
     */
    fun onMissingReceived(context: Context) {
        MissingWidgetProvider.onMissingReceived(context)
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
        LocationWidgetProvider.onLocationChanged(context)
    }
    
    /**
     * Notify widget when location sharing status changes
     */
    fun onSharingStatusChanged(context: Context) {
        LocationWidgetProvider.onLocationChanged(context)
    }
}
