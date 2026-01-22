package com.example.coupleapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching Missing data locally.
 * 
 * Benefits:
 * - Instant load: Shows cached data immediately on screen open
 * - Reduces API calls: Only syncs when data is stale or manually refreshed
 * - Offline-first: Works even without network
 * - Better UX: No 5-6s loading every time entering Missing screen
 */
@Entity(
    tableName = "missing_data",
    indices = [Index(value = ["coupleId", "date"], unique = true)]
)
data class MissingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val coupleId: String,
    val userId: String,
    val userName: String,
    val userAvatarUrl: String?,
    val date: String, // ISO format: yyyy-MM-dd
    val missCount: Int,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for caching Missing summary/streak data.
 * Stores aggregated data to avoid recalculating every time.
 */
@Entity(
    tableName = "missing_summary",
    indices = [Index(value = ["coupleId"], unique = true)]
)
data class MissingSummaryEntity(
    @PrimaryKey
    val coupleId: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalMissCount: Int,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for caching user profiles for Missing feature.
 * Avoids fetching user/partner profiles every time.
 */
@Entity(
    tableName = "missing_user_profile",
    indices = [Index(value = ["coupleId", "userId"], unique = true)]
)
data class MissingUserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val coupleId: String,
    val userId: String,
    val userName: String,
    val userAvatarUrl: String?,
    val isCurrentUser: Boolean,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
