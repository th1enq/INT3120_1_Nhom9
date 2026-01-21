package com.example.coupleapp.util

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Helper class to notify partner when data changes.
 * 
 * This is a CLIENT-SIDE alternative to Cloud Functions.
 * When user uploads data (sleep, location, photo), this helper
 * triggers a sync on partner's device.
 * 
 * How it works:
 * 1. User uploads data to Firestore
 * 2. User calls this helper to notify partner
 * 3. Helper writes to a "sync_triggers" collection
 * 4. Partner's app has a Firestore listener that triggers sync
 * 
 * This approach doesn't require Blaze plan!
 */
object PartnerNotificationHelper {
    
    private const val TAG = "PartnerNotification"
    private const val COLLECTION_SYNC_TRIGGERS = "sync_triggers"
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    /**
     * Notify partner that sleep data has been updated.
     * Call this after saving sleep record.
     */
    suspend fun notifySleepUpdate(context: Context) {
        notifyPartner(context, "sleep")
    }
    
    /**
     * Notify partner that location has been updated.
     * Call this after saving location.
     */
    suspend fun notifyLocationUpdate(context: Context) {
        notifyPartner(context, "location")
    }
    
    /**
     * Notify partner that a new photo/locket has been posted.
     * Call this after uploading locket.
     */
    suspend fun notifyPhotoUpdate(context: Context) {
        notifyPartner(context, "photos")
    }
    
    /**
     * Generic notification to partner.
     * Writes to sync_triggers collection which partner's app listens to.
     */
    private suspend fun notifyPartner(context: Context, dataType: String) {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: return@withContext
                
                // Get partner ID
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                val partnerId = userDoc.getString("partnerId")
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "No partner found, skipping notification")
                    return@withContext
                }
                
                // Write sync trigger for partner
                val triggerData = hashMapOf(
                    "targetUserId" to partnerId,
                    "senderId" to currentUser.uid,
                    "senderName" to (userDoc.getString("displayName") ?: "Partner"),
                    "dataType" to dataType,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "processed" to false
                )
                
                // Use partner ID as document ID so it overwrites previous triggers
                // This prevents spam if user updates multiple times quickly
                firestore.collection(COLLECTION_SYNC_TRIGGERS)
                    .document("${partnerId}_$dataType")
                    .set(triggerData)
                    .await()
                
                Log.d(TAG, "✅ Sync trigger sent to partner for: $dataType")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying partner", e)
            }
        }
    }
    
    /**
     * Clear processed triggers (housekeeping).
     * Call periodically to clean up old triggers.
     */
    suspend fun clearProcessedTriggers() {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: return@withContext
                
                // Delete triggers targeted at current user that were processed
                val triggers = firestore.collection(COLLECTION_SYNC_TRIGGERS)
                    .whereEqualTo("targetUserId", currentUser.uid)
                    .whereEqualTo("processed", true)
                    .get()
                    .await()
                
                for (doc in triggers.documents) {
                    doc.reference.delete().await()
                }
                
                Log.d(TAG, "Cleared ${triggers.size()} processed triggers")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing triggers", e)
            }
        }
    }
}
