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
 * Repository for caching Calendar data.
 * 
 * Cache Strategy:
 * - Couple profile: 1 hour (rarely changes)
 * - Calendar events: 30 minutes (user-created events)
 * 
 * This provides instant UI when opening Calendar screen.
 */
class CalendarCacheRepository(
    private val context: Context = CoupleApplication.instance
) {
    
    companion object {
        private const val TAG = "CalendarCacheRepo"
        private const val PREFS_NAME = "calendar_cache"
        private const val KEY_COUPLE_PROFILE = "couple_profile"
        private const val KEY_CALENDAR_EVENTS = "calendar_events"
        private const val KEY_LOVE_COUNTER = "love_counter"
        
        // Cache freshness durations
        const val PROFILE_FRESHNESS_MS = 60 * 60 * 1000L        // 1 hour
        const val EVENTS_FRESHNESS_MS = 30 * 60 * 1000L          // 30 minutes
        
        @Volatile
        private var INSTANCE: CalendarCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): CalendarCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CalendarCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ Couple Profile ============
    
    /**
     * Get cached couple profile data
     */
    suspend fun getCachedCoupleProfile(coupleId: String): CachedCalendarProfile? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_COUPLE_PROFILE}_$coupleId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, CachedCalendarProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached couple profile", e)
            null
        }
    }
    
    /**
     * Cache couple profile data
     */
    suspend fun cacheCoupleProfile(coupleId: String, profile: CachedCalendarProfile) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_COUPLE_PROFILE}_$coupleId"
            val json = gson.toJson(profile)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.CALENDAR_EVENTS, "profile_$coupleId")
            Log.d(TAG, "📦 Cached couple profile for: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching couple profile", e)
        }
    }
    
    /**
     * Check if couple profile cache is fresh
     */
    fun isProfileCacheFresh(coupleId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.CALENDAR_EVENTS,
            "profile_$coupleId",
            PROFILE_FRESHNESS_MS
        )
    }
    
    // ============ Calendar Events ============
    
    /**
     * Get cached calendar events
     */
    suspend fun getCachedEvents(coupleId: String): List<CachedCalendarEvent>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_CALENDAR_EVENTS}_$coupleId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val type = object : TypeToken<List<CachedCalendarEvent>>() {}.type
            gson.fromJson<List<CachedCalendarEvent>>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached events", e)
            null
        }
    }
    
    /**
     * Cache calendar events
     */
    suspend fun cacheEvents(coupleId: String, events: List<CachedCalendarEvent>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_CALENDAR_EVENTS}_$coupleId"
            val json = gson.toJson(events)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.CALENDAR_EVENTS, "events_$coupleId")
            Log.d(TAG, "📦 Cached ${events.size} calendar events for: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching events", e)
        }
    }
    
    /**
     * Check if events cache is fresh
     */
    fun isEventsCacheFresh(coupleId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.CALENDAR_EVENTS,
            "events_$coupleId",
            EVENTS_FRESHNESS_MS
        )
    }
    
    // ============ Cache Management ============
    
    /**
     * Check if any cached data exists
     */
    suspend fun hasCachedData(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        val hasProfile = getCachedCoupleProfile(coupleId) != null
        val hasEvents = getCachedEvents(coupleId) != null
        hasProfile || hasEvents
    }
    
    /**
     * Invalidate calendar cache (e.g., after adding/editing events)
     */
    suspend fun invalidateCache(coupleId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("${KEY_COUPLE_PROFILE}_$coupleId")
            .remove("${KEY_CALENDAR_EVENTS}_$coupleId")
            .apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.CALENDAR_EVENTS, "profile_$coupleId")
        CacheManager.invalidateCache(context, CacheManager.DataType.CALENDAR_EVENTS, "events_$coupleId")
        Log.d(TAG, "🗑️ Calendar cache invalidated for: $coupleId")
    }
    
    /**
     * Clear all calendar cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.CALENDAR_EVENTS)
        Log.d(TAG, "🗑️ Calendar cache cleared on logout")
    }
}

/**
 * Cacheable data class for couple profile
 */
data class CachedCalendarProfile(
    val coupleId: String,
    val user1Id: String,
    val user1Name: String,
    val user1Nickname: String,
    val user1AvatarUrl: String?,
    val user1DateOfBirth: String?,
    val user2Id: String,
    val user2Name: String,
    val user2Nickname: String,
    val user2AvatarUrl: String?,
    val user2DateOfBirth: String?,
    val relationshipStartDate: String, // ISO format
    val backgroundImageUrl: String
)

/**
 * Cacheable data class for calendar event
 */
data class CachedCalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val date: String, // ISO format
    val time: String?,
    val eventType: String,
    val isRecurring: Boolean,
    val reminderMinutes: Int
)
