package com.example.coupleapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.Coil
import com.example.coupleapp.util.createImageLoaderWithBase64Support
import com.example.coupleapp.worker.BackgroundLocationWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class for initializing Firebase, background workers, and tracking app lifecycle
 */
class CoupleApplication : Application(), Configuration.Provider, LifecycleEventObserver {
    
    companion object {
        var isAppInForeground = false
            private set
        
        // Track if user is currently in chat screen (to avoid duplicate notifications)
        var isUserInChatScreen = false
    }
    
    override fun onCreate() {
        super.onCreate()
        
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
        
        Log.d("CoupleApplication", "Firebase initialized successfully")
        Log.d("CoupleApplication", "Coil ImageLoader with base64 support initialized")
        
        // Schedule background location worker if user is logged in
        scheduleBackgroundLocationIfNeeded()
    }
    
    /**
     * Schedule background location updates if user is logged in and paired
     */
    private fun scheduleBackgroundLocationIfNeeded() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("CoupleApplication", "User logged in, scheduling background location worker")
            BackgroundLocationWorker.schedule(this)
        } else {
            Log.d("CoupleApplication", "No user logged in, skipping background location")
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
            }
            Lifecycle.Event.ON_STOP -> {
                // App moved to background
                isAppInForeground = false
                Log.d("CoupleApplication", "App in BACKGROUND")
            }
            else -> {}
        }
    }
}

