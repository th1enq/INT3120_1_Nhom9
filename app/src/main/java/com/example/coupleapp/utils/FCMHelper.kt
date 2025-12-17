package com.example.coupleapp.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Helper class for managing FCM tokens and sending notifications
 * 
 * Note: For production, push notifications should be sent via a server-side
 * Cloud Function that triggers when new messages are written to the database.
 * This helper manages the client-side token storage.
 */
object FCMHelper {
    private const val TAG = "FCMHelper"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get current FCM token and save to Firestore
     * Call this after user logs in
     */
    suspend fun registerFCMToken() {
        try {
            val userId = auth.currentUser?.uid ?: return
            val token = FirebaseMessaging.getInstance().token.await()
            
            Log.d(TAG, "FCM Token obtained: ${token.take(20)}...")
            
            firestore.collection("users")
                .document(userId)
                .update("fcmToken", token)
                .await()
                
            Log.d(TAG, "FCM Token saved to Firestore for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    /**
     * Remove FCM token when user logs out
     */
    suspend fun unregisterFCMToken() {
        try {
            val userId = auth.currentUser?.uid ?: return
            
            firestore.collection("users")
                .document(userId)
                .update("fcmToken", null)
                .await()
                
            Log.d(TAG, "FCM Token removed from Firestore for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister FCM token", e)
        }
    }

    /**
     * Get partner's FCM token from Firestore
     */
    suspend fun getPartnerFCMToken(partnerId: String): String? {
        return try {
            val doc = firestore.collection("users")
                .document(partnerId)
                .get()
                .await()
            
            doc.getString("fcmToken")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get partner FCM token", e)
            null
        }
    }

    /**
     * Subscribe to a topic for couple-wide notifications
     */
    suspend fun subscribeToCoupleNotifications(coupleId: String) {
        try {
            FirebaseMessaging.getInstance()
                .subscribeToTopic("couple_$coupleId")
                .await()
            Log.d(TAG, "Subscribed to couple notifications: couple_$coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to couple notifications", e)
        }
    }
}
