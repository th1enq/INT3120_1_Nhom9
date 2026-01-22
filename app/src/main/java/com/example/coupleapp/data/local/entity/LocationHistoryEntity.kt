package com.example.coupleapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.coupleapp.data.model.LocationCoordinate
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.data.model.LocationType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Room entity for caching Location History data.
 * 
 * Cache Strategy:
 * - Cache freshness: 2 minutes (location can change frequently)
 * - Background refresh: Sync from Firebase when cache is stale
 * - Immediate update: When user opens history, show cached data immediately
 */
@Entity(
    tableName = "location_history_cache",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["userId", "coupleId"])
    ]
)
data class LocationHistoryEntity(
    @PrimaryKey
    val id: String, // Original Firestore document ID
    val userId: String,
    val coupleId: String,
    val locationName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val arrivalTimeMs: Long,
    val departureTimeMs: Long?, // null means still there
    val durationMinutes: Int,
    val locationType: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to domain model
     */
    fun toLocationHistory(): LocationHistory {
        return LocationHistory(
            id = this.id,
            locationName = this.locationName,
            address = this.address,
            coordinate = LocationCoordinate(
                latitude = this.latitude,
                longitude = this.longitude
            ),
            arrivalTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(arrivalTimeMs),
                ZoneId.systemDefault()
            ),
            departureTime = departureTimeMs?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            },
            durationMinutes = this.durationMinutes,
            locationType = try {
                LocationType.valueOf(locationType)
            } catch (e: Exception) {
                LocationType.OTHER
            }
        )
    }
    
    companion object {
        /**
         * Create from domain model
         */
        fun fromLocationHistory(
            history: LocationHistory,
            userId: String,
            coupleId: String
        ): LocationHistoryEntity {
            return LocationHistoryEntity(
                id = history.id,
                userId = userId,
                coupleId = coupleId,
                locationName = history.locationName,
                address = history.address,
                latitude = history.coordinate.latitude,
                longitude = history.coordinate.longitude,
                arrivalTimeMs = history.arrivalTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),
                departureTimeMs = history.departureTime?.let {
                    it.atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                },
                durationMinutes = history.durationMinutes,
                locationType = history.locationType.name
            )
        }
    }
}

/**
 * Metadata for tracking cache freshness per user
 */
@Entity(
    tableName = "location_history_sync_metadata",
    indices = [Index(value = ["userId", "coupleId"], unique = true)]
)
data class LocationHistorySyncMetadata(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val coupleId: String,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
