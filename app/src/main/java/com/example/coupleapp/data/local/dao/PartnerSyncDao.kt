package com.example.coupleapp.data.local.dao

import androidx.room.*
import com.example.coupleapp.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for partner sleep data operations.
 * Provides Flow-based observation for reactive widget updates.
 */
@Dao
interface PartnerSleepDao {
    
    @Query("SELECT * FROM partner_sleep_data WHERE partnerId = :partnerId ORDER BY date DESC LIMIT 1")
    fun observeLatestSleep(partnerId: String): Flow<PartnerSleepEntity?>
    
    @Query("SELECT * FROM partner_sleep_data WHERE partnerId = :partnerId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSleep(partnerId: String): PartnerSleepEntity?
    
    @Query("SELECT * FROM partner_sleep_data WHERE partnerId = :partnerId AND date = :date")
    suspend fun getSleepByDate(partnerId: String, date: String): PartnerSleepEntity?
    
    @Query("SELECT * FROM partner_sleep_data WHERE partnerId = :partnerId ORDER BY date DESC LIMIT :limit")
    fun observeSleepHistory(partnerId: String, limit: Int = 7): Flow<List<PartnerSleepEntity>>
    
    @Query("SELECT * FROM partner_sleep_data WHERE partnerId = :partnerId ORDER BY date DESC LIMIT :limit")
    suspend fun getSleepHistory(partnerId: String, limit: Int = 7): List<PartnerSleepEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sleepData: PartnerSleepEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(sleepDataList: List<PartnerSleepEntity>)
    
    @Query("DELETE FROM partner_sleep_data WHERE partnerId = :partnerId")
    suspend fun deleteAllForPartner(partnerId: String)
    
    @Query("DELETE FROM partner_sleep_data WHERE lastSyncedAt < :threshold")
    suspend fun deleteStaleData(threshold: Long)
}

/**
 * DAO for partner location data operations.
 */
@Dao
interface PartnerLocationDao {
    
    @Query("SELECT * FROM partner_location_data WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestLocation(partnerId: String): Flow<PartnerLocationEntity?>
    
    @Query("SELECT * FROM partner_location_data WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocation(partnerId: String): PartnerLocationEntity?
    
    @Query("SELECT * FROM partner_location_data WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT :limit")
    fun observeLocationHistory(partnerId: String, limit: Int = 100): Flow<List<PartnerLocationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(location: PartnerLocationEntity): Long
    
    @Query("DELETE FROM partner_location_data WHERE partnerId = :partnerId")
    suspend fun deleteAllForPartner(partnerId: String)
    
    @Query("DELETE FROM partner_location_data WHERE timestamp < :threshold")
    suspend fun deleteOldLocations(threshold: Long)
}

/**
 * DAO for partner photos (Locket) data operations.
 */
@Dao
interface PartnerPhotoDao {
    
    @Query("SELECT * FROM partner_photos WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestPhoto(partnerId: String): Flow<PartnerPhotoEntity?>
    
    @Query("SELECT * FROM partner_photos WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPhoto(partnerId: String): PartnerPhotoEntity?
    
    @Query("SELECT * FROM partner_photos WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT :limit")
    fun observePhotos(partnerId: String, limit: Int = 50): Flow<List<PartnerPhotoEntity>>
    
    @Query("SELECT * FROM partner_photos WHERE partnerId = :partnerId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPhotos(partnerId: String, limit: Int = 50): List<PartnerPhotoEntity>
    
    @Query("SELECT COUNT(*) FROM partner_photos WHERE partnerId = :partnerId AND isRead = 0")
    fun observeUnreadCount(partnerId: String): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM partner_photos WHERE partnerId = :partnerId AND isRead = 0")
    suspend fun getUnreadCount(partnerId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(photo: PartnerPhotoEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(photos: List<PartnerPhotoEntity>)
    
    @Query("UPDATE partner_photos SET isRead = 1 WHERE photoId = :photoId")
    suspend fun markAsRead(photoId: String)
    
    @Query("UPDATE partner_photos SET isRead = 1 WHERE partnerId = :partnerId")
    suspend fun markAllAsRead(partnerId: String)
    
    @Query("DELETE FROM partner_photos WHERE partnerId = :partnerId")
    suspend fun deleteAllForPartner(partnerId: String)
    
    @Query("DELETE FROM partner_photos WHERE timestamp < :threshold")
    suspend fun deleteOldPhotos(threshold: Long)
}

/**
 * DAO for sync metadata tracking.
 */
@Dao
interface SyncMetadataDao {
    
    @Query("SELECT * FROM sync_metadata WHERE dataType = :dataType AND partnerId = :partnerId")
    suspend fun getSyncMetadata(dataType: String, partnerId: String): SyncMetadataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncMetadataEntity): Long
    
    @Query("UPDATE sync_metadata SET lastSyncTimestamp = :timestamp, syncStatus = :status WHERE dataType = :dataType AND partnerId = :partnerId")
    suspend fun updateSyncStatus(dataType: String, partnerId: String, timestamp: Long, status: String)
    
    @Query("DELETE FROM sync_metadata WHERE partnerId = :partnerId")
    suspend fun deleteAllForPartner(partnerId: String)
}
