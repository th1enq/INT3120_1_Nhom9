package com.example.coupleapp.data.repository

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.coupleapp.data.model.FirebaseLocketPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Repository for Firebase Locket operations
 * Handles sending/receiving photos, emojis, drawings, and text
 */
class LocketFirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storageRepository: FirebaseStorageRepository = FirebaseStorageRepository()
) {
    
    companion object {
        private const val TAG = "LocketFirebaseRepo"
        private const val LOCKET_POSTS_COLLECTION = "locket_posts"
        private const val USERS_COLLECTION = "users"
    }
    
    /**
     * Convert Bitmap to Base64 string with compression
     * This allows storing images directly in Firestore without using Storage
     */
    private fun bitmapToBase64(bitmap: Bitmap, maxWidth: Int = 800, quality: Int = 60): String {
        // Resize bitmap if too large
        val resizedBitmap = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // Compress to JPEG
        val byteArrayOutputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        
        // Encode to Base64
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * Send a photo locket (from camera or gallery)
     * Uses Base64 encoding to store directly in Firestore (no Storage needed)
     */
    suspend fun sendPhotoLocket(
        bitmap: Bitmap,
        receiverId: String,
        receiverName: String,
        caption: String = ""
    ): Result<String> {
        return try {
            Log.d(TAG, "sendPhotoLocket: Starting for receiver $receiverId")
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            Log.d(TAG, "sendPhotoLocket: Current user ${currentUser.uid}")
            
            // Compress and convert to Base64 (to avoid Storage)
            Log.d(TAG, "sendPhotoLocket: Converting to Base64")
            val base64Image = bitmapToBase64(bitmap, maxWidth = 800, quality = 60)
            Log.d(TAG, "sendPhotoLocket: Base64 size: ${base64Image.length} chars")
            
            // Check if too large for Firestore (max 1MB per document)
            if (base64Image.length > 900000) { // Leave buffer for other fields
                Log.e(TAG, "sendPhotoLocket: Image too large after compression")
                return Result.failure(Exception("Image too large, please try a smaller image"))
            }
            
            // Get user info
            Log.d(TAG, "sendPhotoLocket: Fetching user info")
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()
            
            val userName = userDoc.getString("displayName") ?: "Unknown"
            val userAvatarUrl = userDoc.getString("profileImageUrl") ?: ""
            val coupleId = userDoc.getString("coupleId") ?: ""
            Log.d(TAG, "sendPhotoLocket: User info - name: $userName, coupleId: $coupleId")
            
            // Create locket post with Base64 data
            val locketPost = FirebaseLocketPost(
                coupleId = coupleId,
                senderId = currentUser.uid,
                senderName = userName,
                senderAvatarUrl = userAvatarUrl,
                receiverId = receiverId,
                receiverName = receiverName,
                type = "photo",
                photoUrl = base64Image, // Store Base64 directly
                caption = caption
            )
            
            // Save to Firestore
            Log.d(TAG, "sendPhotoLocket: Saving to Firestore")
            val docRef = firestore.collection(LOCKET_POSTS_COLLECTION)
                .add(locketPost)
                .await()
            
            Log.d(TAG, "sendPhotoLocket: Success! Document ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "sendPhotoLocket: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send an emoji locket
     */
    suspend fun sendEmojiLocket(
        emoji: String,
        receiverId: String,
        receiverName: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            // Get user info
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()
            
            val userName = userDoc.getString("displayName") ?: "Unknown"
            val userAvatarUrl = userDoc.getString("profileImageUrl") ?: ""
            val coupleId = userDoc.getString("coupleId") ?: ""
            
            // Create locket post
            val locketPost = FirebaseLocketPost(
                coupleId = coupleId,
                senderId = currentUser.uid,
                senderName = userName,
                senderAvatarUrl = userAvatarUrl,
                receiverId = receiverId,
                receiverName = receiverName,
                type = "emoji",
                emoji = emoji
            )
            
            // Save to Firestore
            val docRef = firestore.collection(LOCKET_POSTS_COLLECTION)
                .add(locketPost)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send a drawing locket
     * Uses Base64 encoding to store directly in Firestore (no Storage needed)
     */
    suspend fun sendDrawingLocket(
        drawingBitmap: Bitmap,
        receiverId: String,
        receiverName: String
    ): Result<String> {
        return try {
            Log.d(TAG, "sendDrawingLocket: Starting for receiver $receiverId")
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            Log.d(TAG, "sendDrawingLocket: Current user ${currentUser.uid}")
            
            // Compress and convert to Base64 (to avoid Storage)
            Log.d(TAG, "sendDrawingLocket: Converting to Base64")
            val base64Image = bitmapToBase64(drawingBitmap, maxWidth = 800, quality = 70)
            Log.d(TAG, "sendDrawingLocket: Base64 size: ${base64Image.length} chars")
            
            // Check if too large for Firestore
            if (base64Image.length > 900000) {
                Log.e(TAG, "sendDrawingLocket: Drawing too large after compression")
                return Result.failure(Exception("Drawing too large"))
            }
            
            // Get user info
            Log.d(TAG, "sendDrawingLocket: Fetching user info")
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()
            
            val userName = userDoc.getString("displayName") ?: "Unknown"
            val userAvatarUrl = userDoc.getString("profileImageUrl") ?: ""
            val coupleId = userDoc.getString("coupleId") ?: ""
            Log.d(TAG, "sendDrawingLocket: User info - name: $userName, coupleId: $coupleId")
            
            // Create locket post with Base64 data
            val locketPost = FirebaseLocketPost(
                coupleId = coupleId,
                senderId = currentUser.uid,
                senderName = userName,
                senderAvatarUrl = userAvatarUrl,
                receiverId = receiverId,
                receiverName = receiverName,
                type = "drawing",
                drawingUrl = base64Image // Store Base64 directly
            )
            
            // Save to Firestore
            Log.d(TAG, "sendDrawingLocket: Saving to Firestore")
            val docRef = firestore.collection(LOCKET_POSTS_COLLECTION)
                .add(locketPost)
                .await()
            
            Log.d(TAG, "sendDrawingLocket: Success! Document ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "sendDrawingLocket: Error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send a text locket
     */
    suspend fun sendTextLocket(
        text: String,
        receiverId: String,
        receiverName: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            // Get user info
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()
            
            val userName = userDoc.getString("displayName") ?: "Unknown"
            val userAvatarUrl = userDoc.getString("profileImageUrl") ?: ""
            val coupleId = userDoc.getString("coupleId") ?: ""
            
            // Create locket post
            val locketPost = FirebaseLocketPost(
                coupleId = coupleId,
                senderId = currentUser.uid,
                senderName = userName,
                senderAvatarUrl = userAvatarUrl,
                receiverId = receiverId,
                receiverName = receiverName,
                type = "text",
                textContent = text
            )
            
            // Save to Firestore
            val docRef = firestore.collection(LOCKET_POSTS_COLLECTION)
                .add(locketPost)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get lockets received by current user (realtime)
     */
    fun getReceivedLocketsFlow(): Flow<List<FirebaseLocketPost>> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection(LOCKET_POSTS_COLLECTION)
            .whereEqualTo("receiverId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getReceivedLocketsFlow: Error listening", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val lockets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirebaseLocketPost::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                
                trySend(lockets)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get lockets sent by current user (realtime)
     */
    fun getSentLocketsFlow(): Flow<List<FirebaseLocketPost>> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection(LOCKET_POSTS_COLLECTION)
            .whereEqualTo("senderId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getSentLocketsFlow: Error listening", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val lockets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirebaseLocketPost::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                
                trySend(lockets)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get all lockets for a couple (both sent and received)
     */
    fun getAllLocketsForCoupleFlow(coupleId: String): Flow<List<FirebaseLocketPost>> = callbackFlow {
        val listener = firestore.collection(LOCKET_POSTS_COLLECTION)
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getAllLocketsForCoupleFlow: Error listening", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val lockets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirebaseLocketPost::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                
                trySend(lockets)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Mark a locket as read
     */
    suspend fun markAsRead(locketId: String): Result<Unit> {
        return try {
            firestore.collection(LOCKET_POSTS_COLLECTION)
                .document(locketId)
                .update("isRead", true)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get unread lockets count
     */
    suspend fun getUnreadCount(): Result<Int> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            val snapshot = firestore.collection(LOCKET_POSTS_COLLECTION)
                .whereEqualTo("receiverId", currentUser.uid)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a locket
     */
    suspend fun deleteLocket(locketId: String, locketPost: FirebaseLocketPost): Result<Unit> {
        return try {
            // Delete associated images from storage if exists
            when (locketPost.type) {
                "photo" -> {
                    if (locketPost.photoUrl.isNotEmpty()) {
                        storageRepository.deleteFile(locketPost.photoUrl)
                    }
                }
                "drawing" -> {
                    if (locketPost.drawingUrl.isNotEmpty()) {
                        storageRepository.deleteFile(locketPost.drawingUrl)
                    }
                }
            }
            
            // Delete from Firestore
            firestore.collection(LOCKET_POSTS_COLLECTION)
                .document(locketId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
