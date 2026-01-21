package com.example.coupleapp.widget.observer

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.coupleapp.data.local.entity.PartnerLocationEntity
import com.example.coupleapp.data.local.entity.PartnerPhotoEntity
import com.example.coupleapp.data.local.entity.PartnerSleepEntity
import com.example.coupleapp.data.sync.PartnerSyncRepository
import com.example.coupleapp.widget.LocationWidgetProvider
import com.example.coupleapp.widget.LocketWidgetProvider
import com.example.coupleapp.widget.SleepWidgetProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/**
 * Observes Room Database changes and automatically triggers widget updates.
 * 
 * This is the key component for "reactive" widget updates:
 * 1. Room DB emits Flow when data changes
 * 2. This observer collects the Flow
 * 3. On each emission, triggers widget refresh
 * 
 * Benefits:
 * - Widget always reflects latest data in Room
 * - No polling needed - updates are push-based
 * - Battery efficient - only updates when data actually changes
 * - Works even when main app is closed (via persistent scope)
 * 
 * Architecture:
 * FCM -> Worker -> Room DB -> [RoomWidgetObserver] -> Widget
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
object RoomWidgetObserver {
    
    private const val TAG = "RoomWidgetObserver"
    
    private var observerScope: CoroutineScope? = null
    private var partnerId: String? = null
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Track last update timestamps to debounce rapid updates
    private var lastSleepUpdate = 0L
    private var lastLocationUpdate = 0L
    private var lastPhotoUpdate = 0L
    private const val DEBOUNCE_INTERVAL_MS = 500L // Minimum time between updates
    
    /**
     * Start observing Room Database for partner data changes.
     * Call this when user logs in or app starts.
     * 
     * @param context Application context (use applicationContext to avoid leaks)
     */
    fun startObserving(context: Context) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "User not logged in, cannot start observation")
            return
        }
        
        // Stop any existing observation
        stopObserving()
        
        Log.d(TAG, "Starting Room database observation")
        
        // Create new scope for observation
        observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        // Fetch partner ID and start observing
        observerScope?.launch {
            try {
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                val fetchedPartnerId = userDoc.getString("partnerId")
                if (fetchedPartnerId.isNullOrEmpty()) {
                    Log.d(TAG, "No partner found, skipping observation")
                    return@launch
                }
                
                partnerId = fetchedPartnerId
                Log.d(TAG, "Starting observation for partner: $fetchedPartnerId")
                
                val repository = PartnerSyncRepository.getInstance(context)
                
                // Start observing all data types in parallel
                launch { observeSleepData(context, repository, fetchedPartnerId) }
                launch { observeLocationData(context, repository, fetchedPartnerId) }
                launch { observePhotoData(context, repository, fetchedPartnerId) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting observation", e)
            }
        }
    }
    
    /**
     * Stop observing Room Database.
     * Call this when user logs out or app is destroyed.
     */
    fun stopObserving() {
        Log.d(TAG, "Stopping Room database observation")
        observerScope?.cancel()
        observerScope = null
        partnerId = null
    }
    
    /**
     * Observe partner sleep data and update Sleep widget.
     */
    private suspend fun observeSleepData(
        context: Context,
        repository: PartnerSyncRepository,
        partnerId: String
    ) {
        repository.observePartnerSleep(partnerId)
            .distinctUntilChanged() // Only emit when data actually changes
            .debounce(DEBOUNCE_INTERVAL_MS) // Prevent rapid successive updates
            .catch { e ->
                Log.e(TAG, "Error observing sleep data", e)
            }
            .collect { sleepData ->
                if (sleepData != null && shouldUpdate(lastSleepUpdate)) {
                    Log.d(TAG, "Sleep data changed, updating widget")
                    lastSleepUpdate = System.currentTimeMillis()
                    updateSleepWidget(context, sleepData)
                }
            }
    }
    
    /**
     * Observe partner location data and update Location widget.
     */
    private suspend fun observeLocationData(
        context: Context,
        repository: PartnerSyncRepository,
        partnerId: String
    ) {
        repository.observePartnerLocation(partnerId)
            .distinctUntilChanged()
            .debounce(DEBOUNCE_INTERVAL_MS)
            .catch { e ->
                Log.e(TAG, "Error observing location data", e)
            }
            .collect { locationData ->
                if (locationData != null && shouldUpdate(lastLocationUpdate)) {
                    Log.d(TAG, "Location data changed, updating widget")
                    lastLocationUpdate = System.currentTimeMillis()
                    updateLocationWidget(context, locationData)
                }
            }
    }
    
    /**
     * Observe partner photo data and update Locket widget.
     */
    private suspend fun observePhotoData(
        context: Context,
        repository: PartnerSyncRepository,
        partnerId: String
    ) {
        repository.observePartnerLatestPhoto(partnerId)
            .distinctUntilChanged()
            .debounce(DEBOUNCE_INTERVAL_MS)
            .catch { e ->
                Log.e(TAG, "Error observing photo data", e)
            }
            .collect { photoData ->
                if (photoData != null && shouldUpdate(lastPhotoUpdate)) {
                    Log.d(TAG, "Photo data changed, updating widget")
                    lastPhotoUpdate = System.currentTimeMillis()
                    updateLocketWidget(context, photoData)
                }
            }
    }
    
    /**
     * Check if enough time has passed since last update.
     */
    private fun shouldUpdate(lastUpdate: Long): Boolean {
        return System.currentTimeMillis() - lastUpdate >= DEBOUNCE_INTERVAL_MS
    }
    
    /**
     * Update Sleep widget with new data.
     */
    private suspend fun updateSleepWidget(context: Context, sleepData: PartnerSleepEntity) {
        withContext(Dispatchers.Main) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, SleepWidgetProvider::class.java)
                )
                
                if (widgetIds.isNotEmpty()) {
                    Log.d(TAG, "Triggering Sleep widget update for ${widgetIds.size} widgets")
                    SleepWidgetProvider.updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sleep widget", e)
            }
        }
    }
    
    /**
     * Update Location widget with new data.
     */
    private suspend fun updateLocationWidget(context: Context, locationData: PartnerLocationEntity) {
        withContext(Dispatchers.Main) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, LocationWidgetProvider::class.java)
                )
                
                if (widgetIds.isNotEmpty()) {
                    Log.d(TAG, "Triggering Location widget update for ${widgetIds.size} widgets")
                    LocationWidgetProvider.updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating location widget", e)
            }
        }
    }
    
    /**
     * Update Locket widget with new data.
     */
    private suspend fun updateLocketWidget(context: Context, photoData: PartnerPhotoEntity) {
        withContext(Dispatchers.Main) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, LocketWidgetProvider::class.java)
                )
                
                if (widgetIds.isNotEmpty()) {
                    Log.d(TAG, "Triggering Locket widget update for ${widgetIds.size} widgets")
                    LocketWidgetProvider.forceUpdateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating locket widget", e)
            }
        }
    }
    
}
