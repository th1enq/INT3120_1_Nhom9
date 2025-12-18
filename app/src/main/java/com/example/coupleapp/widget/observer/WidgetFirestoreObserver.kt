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
import kotlinx.coroutines.SupervisorJob
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
    
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * Start observing Firestore for widget data changes
     * Should be called when app starts or when user logs in
     */
    fun startObserving(context: Context) {
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
     * Observe locket posts for the current user
     * When a new locket is received, update the widget immediately
     */
    private fun observeLocketPosts(context: Context, userId: String) {
        // Remove existing listener
        locketListener?.remove()
        
        locketListener = firestore.collection("locket_posts")
            .whereEqualTo("receiverId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5) // Only listen to recent posts
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to locket posts", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && !snapshot.isEmpty) {
                    // Check if there's a new unread locket
                    val hasNewLocket = snapshot.documentChanges.any { change ->
                        change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED
                    }
                    
                    if (hasNewLocket) {
                        Log.d(TAG, "New locket received, updating widget")
                        observerScope.launch {
                            // Invalidate cache and update widget
                            WidgetDataRepository.invalidateLocketCache(context)
                            LocketWidgetProvider.updateWidgets(context)
                        }
                    }
                }
            }
    }
    
    /**
     * Observe missing signals for the current user
     * When a new missing signal is received, update the widget immediately
     */
    private fun observeMissingSignals(context: Context, userId: String) {
        // Get user's coupleId first
        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val coupleId = doc.getString("coupleId") ?: return@addOnSuccessListener
                
                // Remove existing listener
                missingListener?.remove()
                
                missingListener = firestore.collection("missing_signals")
                    .whereEqualTo("coupleId", coupleId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10) // Only listen to recent signals
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening to missing signals", error)
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null && !snapshot.isEmpty) {
                            // Check if there's a new signal from partner
                            val hasNewSignal = snapshot.documentChanges.any { change ->
                                change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED &&
                                change.document.getString("senderId") != userId
                            }
                            
                            if (hasNewSignal) {
                                Log.d(TAG, "New missing signal received, updating widget")
                                observerScope.launch {
                                    // Invalidate cache and update widget
                                    WidgetDataRepository.invalidateMissingCache(context)
                                    MissingWidgetProvider.updateWidgets(context)
                                }
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get coupleId for missing listener", e)
            }
    }
    
    /**
     * Check if observers are currently active
     */
    fun isObserving(): Boolean {
        return locketListener != null || missingListener != null
    }
}
