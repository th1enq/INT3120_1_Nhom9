package com.example.coupleapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing synced partner sleep data.
 * Acts as single source of truth for widget display.
 */
@Entity(
    tableName = "partner_sleep_data",
    indices = [Index(value = ["partnerId", "date"], unique = true)]
)
data class PartnerSleepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val partnerId: String,
    val partnerName: String,
    val date: String, // ISO format: yyyy-MM-dd
    val bedTimeMillis: Long?,
    val wakeTimeMillis: Long?,
    val sleepDurationMinutes: Int,
    val targetDurationMinutes: Int,
    val sleepQuality: String, // EXCELLENT, GOOD, FAIR, POOR
    val sleepScore: Int, // 0-100
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remSleepMinutes: Int,
    val awakeMinutes: Int,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for storing synced partner location data.
 * Enables location widget to show partner's current location.
 */
@Entity(
    tableName = "partner_location_data",
    indices = [Index(value = ["partnerId"])]
)
data class PartnerLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val partnerId: String,
    val partnerName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val address: String?,
    val placeName: String?,
    val timestamp: Long,
    val isOnline: Boolean,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for storing synced partner photos (Locket).
 * Enables locket widget to show latest photos from partner.
 */
@Entity(
    tableName = "partner_photos",
    indices = [Index(value = ["partnerId", "timestamp"])]
)
data class PartnerPhotoEntity(
    @PrimaryKey
    val photoId: String,
    val partnerId: String,
    val partnerName: String,
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String?,
    val timestamp: Long,
    val isRead: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for tracking sync status and preventing duplicate syncs.
 */
@Entity(
    tableName = "sync_metadata",
    indices = [Index(value = ["dataType", "partnerId"], unique = true)]
)
data class SyncMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dataType: String, // "sleep", "location", "photos"
    val partnerId: String,
    val lastSyncTimestamp: Long,
    val lastSyncVersion: String?, // For conflict resolution
    val syncStatus: String // "SUCCESS", "PENDING", "FAILED"
)
