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
        private const val KEY_USER_INFO = "user_info"
        
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
                    date = cached.date,
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
                    date = group.date,
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
    val date: java.time.LocalDate,
    val moments: List<CachedMoment>
)

/**
 * Unified cacheable moment format that can represent any MomentItem type
 */
data class CachedMoment(
    val type: String,
    val id: String,
    val timestamp: String, // ISO format
    val userName: String?,
    val userAvatar: String?,
    
    // Sleep moment fields
    val bedTime: String? = null,
    val wakeUpTime: String? = null,
    val sleepDuration: Int? = null,
    val sleepQuality: String? = null,
    val achievementPercentage: Float? = null,
    
    // Missing moment fields
    val missingCount: Int? = null,
    val message: String? = null,
    
    // Locket moment fields
    val photoUrl: String? = null,
    val caption: String? = null,
    
    // Anniversary moment fields
    val daysTogether: Int? = null,
    val milestone: String? = null,
    val relationshipStartDate: String? = null,
    
    // Event moment fields
    val eventTitle: String? = null,
    val eventDate: String? = null,
    val eventDescription: String? = null,
    val eventEmoji: String? = null,
    val daysUntil: Int? = null,
    
    // Garden moment fields
    val plantName: String? = null,
    val plantStage: String? = null,
    val plantEmoji: String? = null,
    val milestoneType: String? = null,
    
    // Message moment fields
    val messageType: String? = null,
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val messageContent: String? = null
)

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
            userName = userName,
            userAvatar = userAvatar,
            missingCount = missingCount,
            message = message
        )
        is LocketMoment -> CachedMoment(
            type = "locket",
            id = id,
            timestamp = timestamp.toString(),
            userName = userName,
            userAvatar = userAvatar,
            photoUrl = photoUrl,
            caption = caption
        )
        is AnniversaryMoment -> CachedMoment(
            type = "anniversary",
            id = id,
            timestamp = timestamp.toString(),
            userName = null,
            userAvatar = null,
            daysTogether = daysTogether,
            milestone = milestone,
            relationshipStartDate = relationshipStartDate.toString()
        )
        is EventMoment -> CachedMoment(
            type = "event",
            id = id,
            timestamp = timestamp.toString(),
            userName = null,
            userAvatar = null,
            eventTitle = title,
            eventDate = date.toString(),
            eventDescription = description,
            eventEmoji = emoji,
            daysUntil = daysUntil
        )
        is GardenMoment -> CachedMoment(
            type = "garden",
            id = id,
            timestamp = timestamp.toString(),
            userName = null,
            userAvatar = null,
            plantName = plantName,
            plantStage = plantStage,
            plantEmoji = plantEmoji,
            milestoneType = milestoneType
        )
        is MessageMoment -> CachedMoment(
            type = "message",
            id = id,
            timestamp = timestamp.toString(),
            userName = null,
            userAvatar = null,
            messageType = this.type.name,
            senderName = senderName,
            senderAvatar = senderAvatar,
            messageContent = message
        )
        else -> CachedMoment(
            type = "unknown",
            id = "unknown",
            timestamp = java.time.LocalDateTime.now().toString(),
            userName = null,
            userAvatar = null
        )
    }
}

/**
 * Extension to convert CachedMoment back to MomentItem
 */
fun CachedMoment.toMomentItem(): MomentItem? {
    return try {
        val timestamp = java.time.LocalDateTime.parse(this.timestamp)
        
        when (type) {
            "sleep" -> SleepMoment(
                id = id,
                timestamp = timestamp,
                userName = userName ?: "",
                userAvatar = userAvatar,
                bedTime = java.time.LocalTime.parse(bedTime),
                wakeUpTime = java.time.LocalTime.parse(wakeUpTime),
                sleepDuration = sleepDuration ?: 0,
                quality = SleepQuality.valueOf(sleepQuality ?: "GOOD"),
                achievementPercentage = achievementPercentage ?: 0f
            )
            "missing" -> MissingMoment(
                id = id,
                timestamp = timestamp,
                userName = userName ?: "",
                userAvatar = userAvatar,
                missingCount = missingCount ?: 0,
                message = message
            )
            "locket" -> LocketMoment(
                id = id,
                timestamp = timestamp,
                userName = userName ?: "",
                userAvatar = userAvatar,
                photoUrl = photoUrl ?: "",
                caption = caption
            )
            "anniversary" -> AnniversaryMoment(
                id = id,
                timestamp = timestamp,
                daysTogether = daysTogether ?: 0,
                milestone = milestone ?: "",
                relationshipStartDate = java.time.LocalDate.parse(relationshipStartDate)
            )
            "event" -> EventMoment(
                id = id,
                timestamp = timestamp,
                title = eventTitle ?: "",
                date = java.time.LocalDate.parse(eventDate),
                description = eventDescription,
                emoji = eventEmoji ?: "📅",
                daysUntil = daysUntil ?: 0
            )
            "garden" -> GardenMoment(
                id = id,
                timestamp = timestamp,
                plantName = plantName ?: "",
                plantStage = plantStage ?: "",
                plantEmoji = plantEmoji ?: "🌱",
                milestoneType = milestoneType ?: ""
            )
            "message" -> MessageMoment(
                id = id,
                timestamp = timestamp,
                type = MessageNotificationType.valueOf(messageType ?: "RECEIVED"),
                senderName = senderName ?: "",
                senderAvatar = senderAvatar,
                message = messageContent ?: ""
            )
            else -> null
        }
    } catch (e: Exception) {
        Log.e("MomentsCacheRepo", "Error converting cached moment: ${e.message}")
        null
    }
}
