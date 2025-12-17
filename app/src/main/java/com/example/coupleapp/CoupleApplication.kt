package com.example.coupleapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.Coil
import com.example.coupleapp.util.createImageLoaderWithBase64Support
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class for initializing Firebase and tracking app lifecycle
 */
class CoupleApplication : Application(), LifecycleEventObserver {
    
    companion object {
        var isAppInForeground = false
            private set
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
    }
    
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
