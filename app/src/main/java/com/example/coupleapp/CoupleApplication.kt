package com.example.coupleapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.Coil
import com.example.coupleapp.data.sync.PartnerSyncRepository
import com.example.coupleapp.util.createImageLoaderWithBase64Support
import com.example.coupleapp.util.SyncTriggerListener
import com.example.coupleapp.service.SignificantLocationManager
import com.example.coupleapp.service.LocationTrackingService
import com.example.coupleapp.manager.MessageNotificationManager
import com.example.coupleapp.util.PartnerNotificationManager
import com.example.coupleapp.widget.WidgetManager
import com.example.coupleapp.widget.observer.RoomWidgetObserver
import com.example.coupleapp.widget.observer.WidgetFirestoreObserver
import com.example.coupleapp.worker.BackgroundLocationWorker
import com.example.coupleapp.worker.PartnerDataSyncWorker
import com.example.coupleapp.worker.SleepSyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application class for initializing Firebase, background workers, widgets and tracking app lifecycle
 */
@HiltAndroidApp
class CoupleApplication : Application(), Configuration.Provider, LifecycleEventObserver {
    
    companion object {
        var isAppInForeground = false
            private set
        
        // Track if user is currently in chat screen (to avoid duplicate notifications)
        var isUserInChatScreen = false
        
        // Application instance for global context access (used by widgets, etc.)
        lateinit var instance: CoupleApplication
            private set
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Configure Firestore settings
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Enable offline persistence
            .build()
        firestore.firestoreSettings = settings
        
        // Initialize custom Coil ImageLoader with base64 support
        val imageLoader = createImageLoaderWithBase64Support(this)
        Coil.setImageLoader(imageLoader)
        
        // Track app lifecycle for notifications
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Create notification channels for partner notifications
        PartnerNotificationManager.createNotificationChannels(this)
        
        // Initialize widget system (legacy + new)
        initializeWidgets()
        
        Log.d("CoupleApplication", "Firebase initialized successfully")
        Log.d("CoupleApplication", "Coil ImageLoader with base64 support initialized")
        
        // Schedule background workers if user is logged in
        scheduleBackgroundWorkersIfNeeded()
    }
    
    /**
     * Initialize widget system with periodic updates
     */
    private fun initializeWidgets() {
        Log.d("CoupleApplication", "Initializing widget system")
        
        // Initialize legacy widget system (WidgetDataRepository + Firestore Observer)
        WidgetManager.initialize(this)
        
        // Initialize new Room-based widget observer
        // This provides reactive widget updates when Room DB changes
        RoomWidgetObserver.startObserving(this)
        
        // Start Firestore sync trigger listener (alternative to Cloud Functions)
        // This listens for sync requests from partner without needing FCM
        SyncTriggerListener.startListening(this)
        
        Log.d("CoupleApplication", "Widget systems and sync trigger listener initialized")
    }
    
    /**
     * Schedule background workers if user is logged in and paired
     */
    private fun scheduleBackgroundWorkersIfNeeded() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("CoupleApplication", "User logged in, scheduling background workers")
            
            // Battery-efficient location tracking (like Widgetable)
            // Uses Significant Location Changes instead of continuous GPS
            SignificantLocationManager.getInstance(this).startTracking()
            
            // Fallback: periodic location worker (runs every 15 min if significant changes missed)
            BackgroundLocationWorker.schedule(this)
            
            // Sleep sync worker
            scheduleSleepSyncWorker()
            
            // Partner data sync worker (Silent Push fallback - no Cloud Functions needed)
            schedulePartnerDataSync()
        } else {
            Log.d("CoupleApplication", "No user logged in, skipping background workers")
        }
    }
    
    /**
     * Schedule sleep sync worker for accurate sleep tracking
     */
    private fun scheduleSleepSyncWorker() {
        Log.d("CoupleApplication", "Scheduling sleep sync workers")
        SleepSyncWorker.schedulePeriodicSync(this)
        SleepSyncWorker.scheduleMorningSync(this)
        SleepSyncWorker.triggerImmediateSync(this)
    }
    
    /**
     * Schedule periodic partner data sync as fallback for Silent Push.
     * This ensures data stays fresh even if FCM is delayed/blocked.
     */
    private fun schedulePartnerDataSync() {
        applicationScope.launch {
            try {
                val partnerId = PartnerSyncRepository.getInstance(this@CoupleApplication).getPartnerId()
                if (partnerId != null) {
                    Log.d("CoupleApplication", "Scheduling periodic partner data sync for: $partnerId")
                    PartnerDataSyncWorker.schedulePeriodicSync(this@CoupleApplication, partnerId)
                    
                    // Also trigger immediate sync on app start
                    PartnerDataSyncWorker.enqueueRegular(
                        context = this@CoupleApplication,
                        partnerId = partnerId,
                        syncTypes = null,
                        triggerSource = PartnerDataSyncWorker.TRIGGER_APP_OPEN
                    )
                }
            } catch (e: Exception) {
                Log.e("CoupleApplication", "Error scheduling partner data sync", e)
            }
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                // App moved to foreground
                isAppInForeground = true
                Log.d("CoupleApplication", "App in FOREGROUND")
                
                // Restart Room observer (in case it was stopped)
                RoomWidgetObserver.startObserving(this)
                
                // Periodic cache cleanup when app comes to foreground
                LocationTrackingService.cleanupCaches()
            }
            Lifecycle.Event.ON_STOP -> {
                // App moved to background
                isAppInForeground = false
                Log.d("CoupleApplication", "App in BACKGROUND")
                // Note: Don't stop RoomWidgetObserver here - it should keep running
                // to update widgets even when app is in background
            }
            else -> {}
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d("CoupleApplication", "Application terminating, cleaning up resources")
        
        // Clean up all observers and managers to prevent memory leaks
        RoomWidgetObserver.cleanup()
        WidgetFirestoreObserver.cleanup()
        SyncTriggerListener.cleanup()
        MessageNotificationManager.cleanup()
        LocationTrackingService.resetAllCaches()
        
        // Cancel application scope
        applicationScope.cancel()
    }
    
    /**
     * Call this when user logs out to cleanup user-specific resources
     */
    fun onUserLogout() {
        Log.d("CoupleApplication", "User logged out, cleaning up user-specific resources")
        
        // Stop all observers
        RoomWidgetObserver.stopObserving()
        WidgetFirestoreObserver.stopObserving()
        SyncTriggerListener.stopListening()
        MessageNotificationManager.cleanup()
        
        // Reset location tracking caches
        LocationTrackingService.resetAllCaches()
        LocationTrackingService.stopService(this)
        
        // Reset flags
        isUserInChatScreen = false
    }
}

