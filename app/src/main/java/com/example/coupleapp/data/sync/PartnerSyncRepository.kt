package com.example.coupleapp.data.sync

import android.content.Context
import android.util.Log
import com.example.coupleapp.data.local.CoupleAppDatabase
import com.example.coupleapp.data.local.dao.*
import com.example.coupleapp.data.local.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repository for syncing partner data between Firebase and Room.
 * Implements the Repository pattern with Room as the single source of truth.
 * 
 * Architecture:
 * - Firebase -> Room DB -> Widget UI
 * - UI observes Room via Flow
 * - Network calls only update Room, never expose directly to UI
 * 
 * Battery Optimization:
 * - Incremental sync using lastSyncTimestamp
 * - Batch operations to minimize writes
 * - Cache validation before network calls
 */
class PartnerSyncRepository(context: Context) {
    
    companion object {
        private const val TAG = "PartnerSyncRepository"
        
        // Sync data types
        const val DATA_TYPE_SLEEP = "sleep"
        const val DATA_TYPE_LOCATION = "location"
        const val DATA_TYPE_PHOTOS = "photos"
        
        // Sync status
        const val SYNC_STATUS_SUCCESS = "SUCCESS"
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_FAILED = "FAILED"
        
        // Data retention
        private const val SLEEP_RETENTION_DAYS = 30L
        private const val LOCATION_RETENTION_HOURS = 48L
        private const val PHOTO_RETENTION_DAYS = 90L
        
        @Volatile
        private var INSTANCE: PartnerSyncRepository? = null
        
        fun getInstance(context: Context): PartnerSyncRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PartnerSyncRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val database = CoupleAppDatabase.getInstance(context)
    private val sleepDao: PartnerSleepDao = database.partnerSleepDao()
    private val locationDao: PartnerLocationDao = database.partnerLocationDao()
    private val photoDao: PartnerPhotoDao = database.partnerPhotoDao()
    private val syncMetadataDao: SyncMetadataDao = database.syncMetadataDao()
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // ==================== FLOW OBSERVERS (for Widget) ====================
    
    /**
     * Observe partner's latest sleep data.
     * Widget should collect this Flow to get automatic updates.
     */
    fun observePartnerSleep(partnerId: String): Flow<PartnerSleepEntity?> {
        return sleepDao.observeLatestSleep(partnerId).flowOn(Dispatchers.IO)
    }
    
    /**
     * Observe partner's sleep history (last 7 days).
     */
    fun observePartnerSleepHistory(partnerId: String): Flow<List<PartnerSleepEntity>> {
        return sleepDao.observeSleepHistory(partnerId, 7).flowOn(Dispatchers.IO)
    }
    
    /**
     * Observe partner's latest location.
     * Widget should collect this Flow to get real-time location updates.
     */
    fun observePartnerLocation(partnerId: String): Flow<PartnerLocationEntity?> {
        return locationDao.observeLatestLocation(partnerId).flowOn(Dispatchers.IO)
    }
    
    /**
     * Observe partner's latest photo (Locket).
     * Widget should collect this Flow to display new photos immediately.
     */
    fun observePartnerLatestPhoto(partnerId: String): Flow<PartnerPhotoEntity?> {
        return photoDao.observeLatestPhoto(partnerId).flowOn(Dispatchers.IO)
    }
    
    /**
     * Observe unread photo count for badge display.
     */
    fun observeUnreadPhotoCount(partnerId: String): Flow<Int> {
        return photoDao.observeUnreadCount(partnerId).flowOn(Dispatchers.IO)
    }
    
    // ==================== SYNC OPERATIONS (for Worker) ====================
    
    /**
     * Sync all partner data from Firebase to Room.
     * Called by SyncWorker when FCM message arrives.
     * 
     * @param partnerId The partner's user ID
     * @param dataTypes List of data types to sync (null = sync all)
     * @return SyncResult indicating success/failure for each type
     */
    suspend fun syncPartnerData(
        partnerId: String,
        dataTypes: List<String>? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        val typesToSync = dataTypes ?: listOf(DATA_TYPE_SLEEP, DATA_TYPE_LOCATION, DATA_TYPE_PHOTOS)
        
        Log.d(TAG, "Starting sync for partner: $partnerId, types: $typesToSync")
        
        for (dataType in typesToSync) {
            try {
                when (dataType) {
                    DATA_TYPE_SLEEP -> {
                        syncSleepData(partnerId)
                        results[dataType] = true
                    }
                    DATA_TYPE_LOCATION -> {
                        syncLocationData(partnerId)
                        results[dataType] = true
                    }
                    DATA_TYPE_PHOTOS -> {
                        syncPhotosData(partnerId)
                        results[dataType] = true
                    }
                }
                
                // Update sync metadata
                syncMetadataDao.insertOrUpdate(
                    SyncMetadataEntity(
                        dataType = dataType,
                        partnerId = partnerId,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        lastSyncVersion = null,
                        syncStatus = SYNC_STATUS_SUCCESS
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync $dataType for partner $partnerId", e)
                results[dataType] = false
                
                // Update sync metadata with failure
                syncMetadataDao.insertOrUpdate(
                    SyncMetadataEntity(
                        dataType = dataType,
                        partnerId = partnerId,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        lastSyncVersion = null,
                        syncStatus = SYNC_STATUS_FAILED
                    )
                )
            }
        }
        
        SyncResult(
            success = results.values.all { it },
            syncedTypes = results.filter { it.value }.keys.toList(),
            failedTypes = results.filter { !it.value }.keys.toList()
        )
    }
    
    /**
     * Sync sleep data from Firebase to Room.
     */
    private suspend fun syncSleepData(partnerId: String) {
        Log.d(TAG, "Syncing sleep data for partner: $partnerId")
        
        // Get partner info
        val partnerDoc = firestore.collection("users").document(partnerId).get().await()
        val partnerName = partnerDoc.getString("displayName") ?: "Partner"
        
        // Get current user ID for couple ID
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
        
        // Fetch recent sleep records (last 7 days)
        val sevenDaysAgo = LocalDate.now().minusDays(7)
        val sleepRecords = firestore.collection("couples")
            .document(coupleId)
            .collection("sleep_records")
            .whereEqualTo("userId", partnerId)
            .whereGreaterThanOrEqualTo("date", sevenDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(7)
            .get()
            .await()
        
        val sleepEntities = sleepRecords.documents.mapNotNull { doc ->
            try {
                PartnerSleepEntity(
                    partnerId = partnerId,
                    partnerName = partnerName,
                    date = doc.getString("date") ?: return@mapNotNull null,
                    bedTimeMillis = doc.getLong("bedTimeMillis"),
                    wakeTimeMillis = doc.getLong("wakeTimeMillis"),
                    sleepDurationMinutes = doc.getLong("sleepDurationMinutes")?.toInt() ?: 0,
                    targetDurationMinutes = doc.getLong("targetDurationMinutes")?.toInt() ?: 480,
                    sleepQuality = doc.getString("sleepQuality") ?: "GOOD",
                    sleepScore = doc.getLong("sleepScore")?.toInt() ?: 75,
                    deepSleepMinutes = doc.getLong("deepSleepMinutes")?.toInt() ?: 0,
                    lightSleepMinutes = doc.getLong("lightSleepMinutes")?.toInt() ?: 0,
                    remSleepMinutes = doc.getLong("remSleepMinutes")?.toInt() ?: 0,
                    awakeMinutes = doc.getLong("awakeMinutes")?.toInt() ?: 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse sleep record", e)
                null
            }
        }
        
        if (sleepEntities.isNotEmpty()) {
            sleepDao.insertOrUpdateAll(sleepEntities)
            Log.d(TAG, "Saved ${sleepEntities.size} sleep records to Room")
        }
    }
    
    /**
     * Sync location data from Firebase to Room.
     */
    private suspend fun syncLocationData(partnerId: String) {
        Log.d(TAG, "Syncing location data for partner: $partnerId")
        
        // Get partner info
        val partnerDoc = firestore.collection("users").document(partnerId).get().await()
        val partnerName = partnerDoc.getString("displayName") ?: "Partner"
        
        // Get current user ID for couple ID
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
        
        // Fetch latest location
        val locationDoc = firestore.collection("couples")
            .document(coupleId)
            .collection("locations")
            .document(partnerId)
            .get()
            .await()
        
        if (locationDoc.exists()) {
            val timestamp = locationDoc.getLong("timestamp") ?: System.currentTimeMillis()
            val isRecent = System.currentTimeMillis() - timestamp < 5 * 60 * 1000 // 5 minutes
            
            val locationEntity = PartnerLocationEntity(
                partnerId = partnerId,
                partnerName = partnerName,
                latitude = locationDoc.getDouble("latitude") ?: 0.0,
                longitude = locationDoc.getDouble("longitude") ?: 0.0,
                accuracy = locationDoc.getDouble("accuracy")?.toFloat() ?: 0f,
                address = locationDoc.getString("address"),
                placeName = locationDoc.getString("placeName"),
                timestamp = timestamp,
                isOnline = isRecent
            )
            
            locationDao.insertOrUpdate(locationEntity)
            Log.d(TAG, "Saved location data to Room")
        }
    }
    
    /**
     * Sync photos (Locket) data from Firebase to Room.
     */
    private suspend fun syncPhotosData(partnerId: String) {
        Log.d(TAG, "Syncing photos data for partner: $partnerId")
        
        // Get partner info
        val partnerDoc = firestore.collection("users").document(partnerId).get().await()
        val partnerName = partnerDoc.getString("displayName") ?: "Partner"
        
        // Get current user ID
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        
        // Fetch recent photos sent to current user
        val photos = firestore.collection("locket_posts")
            .whereEqualTo("senderId", partnerId)
            .whereEqualTo("receiverId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .await()
        
        val photoEntities = photos.documents.mapNotNull { doc ->
            try {
                PartnerPhotoEntity(
                    photoId = doc.id,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    imageUrl = doc.getString("imageUrl") ?: return@mapNotNull null,
                    thumbnailUrl = doc.getString("thumbnailUrl"),
                    caption = doc.getString("caption"),
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    isRead = doc.getBoolean("isRead") ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse photo record", e)
                null
            }
        }
        
        if (photoEntities.isNotEmpty()) {
            photoDao.insertOrUpdateAll(photoEntities)
            Log.d(TAG, "Saved ${photoEntities.size} photos to Room")
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get partner ID for current user.
     */
    suspend fun getPartnerId(): String? = withContext(Dispatchers.IO) {
        val currentUserId = auth.currentUser?.uid ?: return@withContext null
        val userDoc = firestore.collection("users").document(currentUserId).get().await()
        userDoc.getString("partnerId")
    }
    
    /**
     * Get latest cached data (synchronous, for immediate widget display).
     */
    suspend fun getCachedSleepData(partnerId: String): PartnerSleepEntity? {
        return sleepDao.getLatestSleep(partnerId)
    }
    
    suspend fun getCachedLocationData(partnerId: String): PartnerLocationEntity? {
        return locationDao.getLatestLocation(partnerId)
    }
    
    suspend fun getCachedLatestPhoto(partnerId: String): PartnerPhotoEntity? {
        return photoDao.getLatestPhoto(partnerId)
    }
    
    /**
     * Mark photo as read.
     */
    suspend fun markPhotoAsRead(photoId: String) = withContext(Dispatchers.IO) {
        photoDao.markAsRead(photoId)
    }
    
    /**
     * Clean up old data to save storage.
     * Should be called periodically.
     */
    suspend fun cleanupOldData() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        // Delete sleep data older than 30 days
        val sleepThreshold = now - (SLEEP_RETENTION_DAYS * 24 * 60 * 60 * 1000)
        sleepDao.deleteStaleData(sleepThreshold)
        
        // Delete location data older than 48 hours
        val locationThreshold = now - (LOCATION_RETENTION_HOURS * 60 * 60 * 1000)
        locationDao.deleteOldLocations(locationThreshold)
        
        // Delete photos older than 90 days
        val photoThreshold = now - (PHOTO_RETENTION_DAYS * 24 * 60 * 60 * 1000)
        photoDao.deleteOldPhotos(photoThreshold)
        
        Log.d(TAG, "Cleaned up old data")
    }
    
    /**
     * Clear all partner data (call on logout or partner change).
     */
    suspend fun clearAllPartnerData(partnerId: String) = withContext(Dispatchers.IO) {
        sleepDao.deleteAllForPartner(partnerId)
        locationDao.deleteAllForPartner(partnerId)
        photoDao.deleteAllForPartner(partnerId)
        syncMetadataDao.deleteAllForPartner(partnerId)
        Log.d(TAG, "Cleared all data for partner: $partnerId")
    }
}

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val success: Boolean,
    val syncedTypes: List<String>,
    val failedTypes: List<String>
)
