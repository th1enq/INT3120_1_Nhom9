package com.example.coupleapp.data.local.dao

import androidx.room.*
import com.example.coupleapp.data.local.entity.MissingEntity
import com.example.coupleapp.data.local.entity.MissingSummaryEntity
import com.example.coupleapp.data.local.entity.MissingUserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Missing data caching operations.
 * Provides both suspend functions for one-time reads and Flow for reactive updates.
 */
@Dao
interface MissingDao {
    
    // ============ Missing Count Data ============
    
    /**
     * Get missing data for a specific date and user
     */
    @Query("SELECT * FROM missing_data WHERE coupleId = :coupleId AND userId = :userId AND date = :date")
    suspend fun getMissingByDate(coupleId: String, userId: String, date: String): MissingEntity?
    
    /**
     * Get all missing entries for a specific date (for all users in couple)
     */
    @Query("SELECT * FROM missing_data WHERE coupleId = :coupleId AND date = :date")
    suspend fun getMissingByDateOnly(coupleId: String, date: String): List<MissingEntity>
    
    /**
     * Get all missing data for a couple within last N days, ordered by date descending
     * IMPORTANT: Each day can have 2 records (one per user), so we need days * 2 limit
     * But to be safe with different scenarios, we use days * 3 to ensure we get all data
     */
    @Query("""
        SELECT * FROM missing_data 
        WHERE coupleId = :coupleId 
        ORDER BY date DESC, userId ASC 
        LIMIT :days * 3
    """)
    suspend fun getMissingHistory(coupleId: String, days: Int = 7): List<MissingEntity>
    
    /**
     * Observe missing data changes for reactive UI updates
     * IMPORTANT: Each day can have 2 records (one per user), so we need days * 3 to be safe
     */
    @Query("""
        SELECT * FROM missing_data 
        WHERE coupleId = :coupleId 
        ORDER BY date DESC, userId ASC 
        LIMIT :days * 3
    """)
    fun observeMissingHistory(coupleId: String, days: Int = 7): Flow<List<MissingEntity>>
    
    /**
     * Get today's count for a user
     */
    @Query("SELECT missCount FROM missing_data WHERE coupleId = :coupleId AND userId = :userId AND date = :date")
    suspend fun getTodayCount(coupleId: String, userId: String, date: String): Int?
    
    /**
     * Insert or update missing data
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(missing: MissingEntity): Long
    
    /**
     * Insert or update multiple records
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(missingList: List<MissingEntity>)
    
    /**
     * Update miss count for a specific record (optimistic update)
     */
    @Query("""
        UPDATE missing_data 
        SET missCount = :newCount, lastSyncedAt = :syncTime 
        WHERE coupleId = :coupleId AND userId = :userId AND date = :date
    """)
    suspend fun updateMissCount(coupleId: String, userId: String, date: String, newCount: Int, syncTime: Long = System.currentTimeMillis())
    
    /**
     * Delete old data (older than N days)
     */
    @Query("DELETE FROM missing_data WHERE date < :oldestDate")
    suspend fun deleteOldData(oldestDate: String)
    
    /**
     * Delete all data for a couple (on logout/unlink)
     */
    @Query("DELETE FROM missing_data WHERE coupleId = :coupleId")
    suspend fun deleteAllForCouple(coupleId: String)
    
    // ============ Missing Summary/Streak Data ============
    
    /**
     * Get cached summary for a couple
     */
    @Query("SELECT * FROM missing_summary WHERE coupleId = :coupleId")
    suspend fun getSummary(coupleId: String): MissingSummaryEntity?
    
    /**
     * Observe summary changes
     */
    @Query("SELECT * FROM missing_summary WHERE coupleId = :coupleId")
    fun observeSummary(coupleId: String): Flow<MissingSummaryEntity?>
    
    /**
     * Insert or update summary
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: MissingSummaryEntity)
    
    /**
     * Delete summary for a couple
     */
    @Query("DELETE FROM missing_summary WHERE coupleId = :coupleId")
    suspend fun deleteSummary(coupleId: String)
    
    // ============ User Profile Cache ============
    
    /**
     * Get cached user profiles for a couple
     */
    @Query("SELECT * FROM missing_user_profile WHERE coupleId = :coupleId")
    suspend fun getUserProfiles(coupleId: String): List<MissingUserProfileEntity>
    
    /**
     * Get current user profile
     */
    @Query("SELECT * FROM missing_user_profile WHERE coupleId = :coupleId AND isCurrentUser = 1")
    suspend fun getCurrentUserProfile(coupleId: String): MissingUserProfileEntity?
    
    /**
     * Get partner profile
     */
    @Query("SELECT * FROM missing_user_profile WHERE coupleId = :coupleId AND isCurrentUser = 0")
    suspend fun getPartnerProfile(coupleId: String): MissingUserProfileEntity?
    
    /**
     * Insert or update user profiles
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: MissingUserProfileEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfiles(profiles: List<MissingUserProfileEntity>)
    
    /**
     * Delete all profiles for a couple
     */
    @Query("DELETE FROM missing_user_profile WHERE coupleId = :coupleId")
    suspend fun deleteProfiles(coupleId: String)
    
    // ============ Cache Freshness Check ============
    
    /**
     * Check if cache is fresh (synced within threshold)
     * Returns true if data was synced within the specified milliseconds
     */
    @Query("SELECT EXISTS(SELECT 1 FROM missing_data WHERE coupleId = :coupleId AND lastSyncedAt > :threshold)")
    suspend fun isCacheFresh(coupleId: String, threshold: Long): Boolean
    
    /**
     * Get the last sync time for a couple
     */
    @Query("SELECT MAX(lastSyncedAt) FROM missing_data WHERE coupleId = :coupleId")
    suspend fun getLastSyncTime(coupleId: String): Long?
}
