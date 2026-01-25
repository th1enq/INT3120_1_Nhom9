package com.example.coupleapp.widget.observer

import android.content.Context
import android.util.Log
import com.example.coupleapp.widget.LocketWidgetProvider
import com.example.coupleapp.widget.MissingWidgetProvider
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Observes Firestore for realtime changes and updates widgets accordingly
 * This enables immediate widget updates when partner sends data
 * 
 * Battery optimization:
 * - Uses Firestore snapshot listeners (efficient, server-push based)
 * - Only listens when widgets are active
 * - Automatically unsubscribes when app is destroyed
 */
object WidgetFirestoreObserver {
    
    private const val TAG = "WidgetFirestoreObserver"
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var locketListener: ListenerRegistration? = null
    private var missingListener: ListenerRegistration? = null
    
    // Make scope nullable and properly managed to avoid memory leaks
    private var observerScope: CoroutineScope? = null
    private var scopeJob: Job? = null
    
    /**
     * Start observing Firestore for widget data changes
     * Should be called when app starts or when user logs in
     */
    fun startObserving(context: Context) {
        // Create a new scope if not exists
        if (observerScope == null) {
            scopeJob = SupervisorJob()
            observerScope = CoroutineScope(scopeJob!! + Dispatchers.Main)
        }
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, skipping Firestore observation")
            return
        }
        
        Log.d(TAG, "Starting Firestore observation for widgets")
        
        // Observe locket posts
        observeLocketPosts(context, currentUser.uid)
        
        // Observe missing signals
        observeMissingSignals(context, currentUser.uid)
    }
    
    /**
     * Stop all Firestore observers
     * Should be called when app is destroyed or user logs out
     */
    fun stopObserving() {
        Log.d(TAG, "Stopping Firestore observation")
        
        locketListener?.remove()
        locketListener = null
        
        missingListener?.remove()
        missingListener = null
    }
    
    /**
     * Full cleanup - cancel scope and remove all listeners
     * Call this when app is terminated or user logs out
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up WidgetFirestoreObserver")
        stopObserving()
        scopeJob?.cancel()
        scopeJob = null
        observerScope = null
    }
    
    /**
     * Observe locket posts for the current user
     * When a new locket is received, update the widget immediately
     * Uses "locket_posts" collection which is where lockets are stored
     */
    private fun observeLocketPosts(context: Context, userId: String) {
        try {
            // Remove existing listener
            locketListener?.remove()
            
            // Listen for lockets where current user is the receiver
            locketListener = firestore.collection("locket_posts")
                .whereEqualTo("receiverId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5) // Only listen to recent posts
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Log error but don't crash - common for missing indexes
                        Log.e(TAG, "Error listening to locket posts (may need Firestore index)", error)
                        return@addSnapshotListener
                    }
                    
                    try {
                        if (snapshot != null && !snapshot.isEmpty) {
                            // Check if there's a new unread locket
                            val hasNewLocket = snapshot.documentChanges.any { change ->
                                change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED &&
                                change.document.getBoolean("isRead") != true
                            }
                            
                            if (hasNewLocket) {
                                Log.d(TAG, "New locket received from partner, updating widget")
                                observerScope?.launch {
                                    // Invalidate cache and update widget
                                    WidgetDataRepository.invalidateLocketCache(context)
                                    LocketWidgetProvider.updateWidgets(context)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing locket snapshot", e)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up locket listener", e)
        }
    }
    
    /**
     * Observe missing records for the current user
     * When partner sends a missing signal, update the widget immediately
     * Uses "missing_records" collection which stores daily missing counts
     */
    private fun observeMissingSignals(context: Context, userId: String) {
        try {
            // Get user's coupleId and partnerId first
            firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    try {
                        val partnerId = doc.getString("partnerId") ?: return@addOnSuccessListener
                        val coupleId = doc.getString("coupleId") 
                            ?: listOf(userId, partnerId).sorted().joinToString("_")
                        
                        // Remove existing listener
                        missingListener?.remove()
                        
                        // Listen for missing records - look for partner's records today
                        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        val partnerRecordId = "${coupleId}_${partnerId}_$today"
                        
                        // Listen to partner's missing record for today
                        missingListener = firestore.collection("missing_records")
                            .document(partnerRecordId)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Log.e(TAG, "Error listening to missing records", error)
                                    return@addSnapshotListener
                                }
                                
                                try {
                                    if (snapshot != null && snapshot.exists()) {
                                        val count = snapshot.getLong("count")?.toInt() ?: 0
                                        if (count > 0) {
                                            Log.d(TAG, "Partner missing count updated: $count, updating widget")
                                            observerScope?.launch {
                                                // Invalidate cache and update widget
                                                WidgetDataRepository.invalidateMissingCache(context)
                                                MissingWidgetProvider.updateWidgets(context)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing missing snapshot", e)
                                }
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up missing listener", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get partnerId for missing listener", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeMissingSignals", e)
        }
    }
    
    /**
     * Check if observers are currently active
     */
    fun isObserving(): Boolean {
        return locketListener != null || missingListener != null
    }
}
