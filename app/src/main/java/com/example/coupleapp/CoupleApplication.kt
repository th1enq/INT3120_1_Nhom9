package com.example.coupleapp

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import coil.Coil
import com.example.coupleapp.util.createImageLoaderWithBase64Support
import com.example.coupleapp.worker.BackgroundLocationWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class for initializing Firebase and background workers
 */
class CoupleApplication : Application(), Configuration.Provider {
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
}

