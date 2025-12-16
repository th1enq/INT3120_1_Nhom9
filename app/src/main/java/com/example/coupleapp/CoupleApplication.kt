package com.example.coupleapp

import android.app.Application
import android.util.Log
import coil.Coil
import com.example.coupleapp.util.createImageLoaderWithBase64Support
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class for initializing Firebase
 */
class CoupleApplication : Application() {
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
    }
}
