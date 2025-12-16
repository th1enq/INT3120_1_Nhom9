package com.example.coupleapp

import android.app.Application
import android.util.Log
// Temporarily disabled Firebase imports
// import com.google.firebase.FirebaseApp
// import com.google.firebase.firestore.FirebaseFirestore
// import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class for initializing Firebase
 * NOTE: Firebase initialization is temporarily disabled
 */
class CoupleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Temporarily disabled Firebase initialization
        /*
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Configure Firestore settings
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Enable offline persistence
            .build()
        firestore.firestoreSettings = settings
        
        Log.d("CoupleApplication", "Firebase initialized successfully")
        */
        
        Log.d("CoupleApplication", "Application started (Firebase disabled)")
    }
}
