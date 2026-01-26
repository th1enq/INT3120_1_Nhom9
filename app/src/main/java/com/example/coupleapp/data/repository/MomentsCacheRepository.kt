package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.cache.CacheManager
import com.example.coupleapp.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Repository for caching Moments data.
 * 
 * Cache Strategy:
 * - Moments list: 15 minutes freshness (aggregates from multiple sources)
 * - Since moments combine data from sleep, missing, locket, etc.,
 *   they change when any source updates
 * 
 * Usage:
 * 1. On screen open: Load from cache immediately (instant UI)
 * 2. If cache exists: Show cached data, refresh in background if stale
 * 3. Force refresh on pull-to-refresh
 */
class MomentsCacheRepository(
    private val context: Context = CoupleApplication.instance
) {
    
    companion object {
        private const val TAG = "MomentsCacheRepo"
        private const val PREFS_NAME = "moments_cache"
        private const val KEY_MOMENTS_GROUPS = "moments_groups"
        
        // Cache freshness - 15 minutes since it aggregates multiple sources
        const val MOMENTS_FRESHNESS_MS = 15 * 60 * 1000L
        
        @Volatile
        private var INSTANCE: MomentsCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): MomentsCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MomentsCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ Moments Groups ============
    
    /**
     * Get cached moments groups for a couple
     */
    suspend fun getCachedMomentsGroups(coupleId: String): List<MomentsGroup>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_MOMENTS_GROUPS}_$coupleId"
            val json = prefs.getString(key, null) ?: return@withContext null
            
            // Custom deserialization for polymorphic MomentItem
            val type = object : TypeToken<List<CachedMomentsGroup>>() {}.type
            val cachedGroups = gson.fromJson<List<CachedMomentsGroup>>(json, type)
            
            // Convert cached groups back to MomentsGroup
            cachedGroups.map { cached ->
                MomentsGroup(
                    section = TimelineSection(
                        date = LocalDate.parse(cached.sectionDate),
                        label = cached.sectionLabel
                    ),
                    moments = cached.moments.mapNotNull { it.toMomentItem() }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached moments", e)
            null
        }
    }
    
    /**
     * Cache moments groups
     */
    suspend fun cacheMomentsGroups(coupleId: String, groups: List<MomentsGroup>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_MOMENTS_GROUPS}_$coupleId"
            
            // Convert to cacheable format
            val cachedGroups = groups.map { group ->
                CachedMomentsGroup(
                    sectionDate = group.section.date.toString(),
                    sectionLabel = group.section.label,
                    moments = group.moments.map { it.toCachedMoment() }
                )
            }
            
            val json = gson.toJson(cachedGroups)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, "moments_data", coupleId)
            Log.d(TAG, "📦 Cached ${groups.sumOf { it.moments.size }} moments in ${groups.size} groups for couple: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching moments", e)
        }
    }
    
    /**
     * Check if moments cache is fresh
     */
    fun isMomentsCacheFresh(coupleId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            "moments_data",
            coupleId,
            MOMENTS_FRESHNESS_MS
        )
    }
    
    /**
     * Check if cached moments exist
     */
    suspend fun hasCachedMoments(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        val key = "${KEY_MOMENTS_GROUPS}_$coupleId"
        prefs.getString(key, null) != null
    }
    
    // ============ Cache Management ============
    
    /**
     * Invalidate moments cache (e.g., after new activity)
     */
    suspend fun invalidateCache(coupleId: String) = withContext(Dispatchers.IO) {
        val key = "${KEY_MOMENTS_GROUPS}_$coupleId"
        prefs.edit().remove(key).apply()
        CacheManager.invalidateCache(context, "moments_data", coupleId)
        Log.d(TAG, "🗑️ Moments cache invalidated for couple: $coupleId")
    }
    
    /**
     * Clear all moments cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        CacheManager.invalidateCache(context, "moments_data")
        Log.d(TAG, "🗑️ Moments cache cleared on logout")
    }
}

// ============ Cacheable Data Classes ============

/**
 * Wrapper for caching MomentsGroup with serializable moments
 */
data class CachedMomentsGroup(
    val sectionDate: String, // LocalDate as string
    val sectionLabel: String,
    val moments: List<CachedMoment>
)

/**
 * Unified cacheable moment format that can represent any MomentItem type
 */
data class CachedMoment(
    val type: String,
    val id: String,
    val timestamp: String, // ISO format
    
    // Sleep moment fields
    val userName: String? = null,
    val userAvatar: String? = null,
    val bedTime: String? = null,
    val wakeUpTime: String? = null,
    val sleepDuration: Int? = null,
    val sleepQuality: String? = null,
    val achievementPercentage: Float? = null,
    
    // Missing moment fields
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val receiverName: String? = null,
    val receiverAvatar: String? = null,
    val missCount: Int? = null,
    
    // Locket moment fields
    val locketType: String? = null,
    val content: String? = null,
    val caption: String? = null,
    
    // Anniversary moment fields
    val daysTogether: Long? = null,
    val monthsTogether: Long? = null,
    val yearsTogether: Long? = null,
    val user1Name: String? = null,
    val user1Avatar: String? = null,
    val user2Name: String? = null,
    val user2Avatar: String? = null,
    
    // Event moment fields
    val eventTitle: String? = null,
    val eventDescription: String? = null,
    val eventDate: String? = null,
    val eventType: String? = null,
    val daysUntil: Long? = null,
    
    // Garden moment fields
    val plantName: String? = null,
    val plantEmoji: String? = null,
    val gardenEventType: String? = null,
    val growthStage: Int? = null,
    val message: String? = null,
    
    // Message moment fields
    val messagePreview: String? = null,
    val messageCount: Int? = null,
    val isRead: Boolean? = null,
    
    // Calendar memory moment fields
    val daysAgo: Long? = null
)

// ============ Extension Functions ============

/**
 * Extension to convert MomentItem to CachedMoment
 */
fun MomentItem.toCachedMoment(): CachedMoment {
    return when (this) {
        is SleepMoment -> CachedMoment(
            type = "sleep",
            id = id,
            timestamp = timestamp.toString(),
            userName = userName,
            userAvatar = userAvatar,
            bedTime = bedTime.toString(),
            wakeUpTime = wakeUpTime.toString(),
            sleepDuration = sleepDuration,
            sleepQuality = quality.name,
            achievementPercentage = achievementPercentage
        )
        is MissingMoment -> CachedMoment(
            type = "missing",
            id = id,
            timestamp = timestamp.toString(),
            senderName = senderName,
            senderAvatar = senderAvatar,
            receiverName = receiverName,
            receiverAvatar = receiverAvatar,
            missCount = missCount
        )
        is LocketMoment -> CachedMoment(
            type = "locket",
            id = id,
            timestamp = timestamp.toString(),
            senderName = senderName,
            senderAvatar = senderAvatar,
            locketType = locketType.name,
            content = content,
            caption = caption
        )
        is AnniversaryMoment -> CachedMoment(
            type = "anniversary",
            id = id,
            timestamp = timestamp.toString(),
            daysTogether = daysTogether,
            monthsTogether = monthsTogether,
            yearsTogether = yearsTogether,
            user1Name = user1Name,
            user1Avatar = user1Avatar,
            user2Name = user2Name,
            user2Avatar = user2Avatar
        )
        is EventMoment -> CachedMoment(
            type = "event",
            id = id,
            timestamp = timestamp.toString(),
            eventTitle = title,
            eventDescription = description,
            eventDate = eventDate.toString(),
            eventType = eventType.name,
            daysUntil = daysUntil
        )
        is GardenMoment -> CachedMoment(
            type = "garden",
            id = id,
            timestamp = timestamp.toString(),
            userName = userName,
            userAvatar = userAvatar,
            plantName = plantName,
            plantEmoji = plantEmoji,
            gardenEventType = eventType.name,
            growthStage = growthStage,
            message = message
        )
        is MessageMoment -> CachedMoment(
            type = "message",
            id = id,
            timestamp = timestamp.toString(),
            senderName = senderName,
            senderAvatar = senderAvatar,
            messagePreview = messagePreview,
            messageCount = messageCount,
            isRead = isRead
        )
        is CalendarMemoryMoment -> CachedMoment(
            type = "calendar_memory",
            id = id,
            timestamp = timestamp.toString(),
            eventTitle = title,
            eventDescription = description,
            eventDate = eventDate.toString(),
            eventType = eventType.name,
            daysAgo = daysAgo
        )
    }
}

/**
 * Extension to convert CachedMoment back to MomentItem
 */
fun CachedMoment.toMomentItem(): MomentItem? {
    return try {
        val timestamp = LocalDateTime.parse(this.timestamp)
        
        when (type) {
            "sleep" -> SleepMoment(
                id = id,
                timestamp = timestamp,
                userName = userName ?: "",
                userAvatar = userAvatar,
                bedTime = LocalTime.parse(bedTime),
                wakeUpTime = LocalTime.parse(wakeUpTime),
                sleepDuration = sleepDuration ?: 0,
                quality = SleepQuality.valueOf(sleepQuality ?: "GOOD"),
                achievementPercentage = achievementPercentage ?: 0f
            )
            "missing" -> MissingMoment(
                id = id,
                timestamp = timestamp,
                senderName = senderName ?: "",
                senderAvatar = senderAvatar,
                receiverName = receiverName ?: "",
                receiverAvatar = receiverAvatar,
                missCount = missCount ?: 0
            )
            "locket" -> LocketMoment(
                id = id,
                timestamp = timestamp,
                senderName = senderName ?: "",
                senderAvatar = senderAvatar,
                locketType = try { LocketType.valueOf(locketType ?: "PHOTO") } catch (e: Exception) { LocketType.PHOTO },
                content = content ?: "",
                caption = caption
            )
            "anniversary" -> AnniversaryMoment(
                id = id,
                timestamp = timestamp,
                daysTogether = daysTogether ?: 0L,
                monthsTogether = monthsTogether ?: 0L,
                yearsTogether = yearsTogether ?: 0L,
                user1Name = user1Name ?: "",
                user1Avatar = user1Avatar,
                user2Name = user2Name ?: "",
                user2Avatar = user2Avatar
            )
            "event" -> EventMoment(
                id = id,
                timestamp = timestamp,
                title = eventTitle ?: "",
                description = eventDescription,
                eventDate = LocalDate.parse(eventDate),
                eventType = try { MomentEventType.valueOf(eventType ?: "REMINDER") } catch (e: Exception) { MomentEventType.REMINDER },
                daysUntil = daysUntil ?: 0L
            )
            "garden" -> GardenMoment(
                id = id,
                timestamp = timestamp,
                userName = userName ?: "",
                userAvatar = userAvatar,
                plantName = plantName ?: "",
                plantEmoji = plantEmoji ?: "🌱",
                eventType = try { GardenEventType.valueOf(gardenEventType ?: "PLANTED") } catch (e: Exception) { GardenEventType.PLANTED },
                growthStage = growthStage ?: 0,
                message = message ?: ""
            )
            "message" -> MessageMoment(
                id = id,
                timestamp = timestamp,
                senderName = senderName ?: "",
                senderAvatar = senderAvatar,
                messagePreview = messagePreview ?: "",
                messageCount = messageCount ?: 0,
                isRead = isRead ?: false
            )
            "calendar_memory" -> CalendarMemoryMoment(
                id = id,
                timestamp = timestamp,
                title = eventTitle ?: "",
                description = eventDescription,
                eventDate = LocalDate.parse(eventDate),
                eventType = try { MomentEventType.valueOf(eventType ?: "REMINDER") } catch (e: Exception) { MomentEventType.REMINDER },
                daysAgo = daysAgo ?: 0L
            )
            else -> null
        }
    } catch (e: Exception) {
        Log.e("MomentsCacheRepo", "Error converting cached moment: ${e.message}")
        null
    }
}
