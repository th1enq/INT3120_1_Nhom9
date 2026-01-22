package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.cache.CacheManager
import com.example.coupleapp.data.model.FirebaseSleepRecord
import com.example.coupleapp.data.model.FirebaseSleepSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for caching Sleep data (settings, records, history).
 * 
 * Cache Strategy:
 * - Sleep Settings: 1 hour freshness (rarely changes)
 * - Sleep History: 30 minutes freshness (changes once per day typically)
 * - Today's Record: 10 minutes freshness (may update during sleep tracking)
 * 
 * Usage:
 * 1. On screen open: Load from cache immediately (instant UI)
 * 2. If cache exists: Show cached data, refresh in background if stale
 * 3. Force refresh on pull-to-refresh
 */
class SleepCacheRepository(
    private val context: Context = CoupleApplication.instance
) {
    
    companion object {
        private const val TAG = "SleepCacheRepo"
        private const val PREFS_NAME = "sleep_cache"
        private const val KEY_SLEEP_SETTINGS = "sleep_settings"
        private const val KEY_SLEEP_HISTORY = "sleep_history"
        private const val KEY_TODAY_RECORD = "today_record"
        private const val KEY_LAST_HISTORY_DATE = "last_history_date"
        
        // Cache freshness durations
        const val SETTINGS_FRESHNESS_MS = 60 * 60 * 1000L       // 1 hour - settings rarely change
        const val HISTORY_FRESHNESS_MS = 30 * 60 * 1000L         // 30 minutes - history changes once/day
        const val TODAY_RECORD_FRESHNESS_MS = 10 * 60 * 1000L    // 10 minutes - may update during tracking
        
        @Volatile
        private var INSTANCE: SleepCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): SleepCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SleepCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ Sleep Settings ============
    
    /**
     * Get cached sleep settings for a user
     */
    suspend fun getCachedSettings(userId: String): FirebaseSleepSettings? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_SLEEP_SETTINGS}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, FirebaseSleepSettings::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached settings", e)
            null
        }
    }
    
    /**
     * Cache sleep settings
     */
    suspend fun cacheSettings(userId: String, settings: FirebaseSleepSettings) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_SLEEP_SETTINGS}_$userId"
            val json = gson.toJson(settings)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.SLEEP_DATA, "settings_$userId")
            Log.d(TAG, "📦 Cached sleep settings for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching settings", e)
        }
    }
    
    /**
     * Check if settings cache is fresh
     */
    fun isSettingsCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.SLEEP_DATA,
            "settings_$userId",
            SETTINGS_FRESHNESS_MS
        )
    }
    
    // ============ Sleep History ============
    
    /**
     * Get cached sleep history for a user
     */
    suspend fun getCachedHistory(userId: String): List<FirebaseSleepRecord>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_SLEEP_HISTORY}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val type = object : TypeToken<List<FirebaseSleepRecord>>() {}.type
            gson.fromJson<List<FirebaseSleepRecord>>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached history", e)
            null
        }
    }
    
    /**
     * Cache sleep history
     */
    suspend fun cacheHistory(userId: String, history: List<FirebaseSleepRecord>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_SLEEP_HISTORY}_$userId"
            val json = gson.toJson(history)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.SLEEP_DATA, "history_$userId")
            Log.d(TAG, "📦 Cached ${history.size} sleep records for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching history", e)
        }
    }
    
    /**
     * Check if history cache is fresh
     */
    fun isHistoryCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.SLEEP_DATA,
            "history_$userId",
            HISTORY_FRESHNESS_MS
        )
    }
    
    // ============ Today's Record ============
    
    /**
     * Get cached today's sleep record
     */
    suspend fun getCachedTodayRecord(userId: String): FirebaseSleepRecord? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_TODAY_RECORD}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val record = gson.fromJson(json, FirebaseSleepRecord::class.java)
            
            // Validate that the cached record is from today
            val today = java.time.LocalDate.now()
            val recordDate = record.date?.toDate()?.toInstant()
                ?.atZone(java.time.ZoneId.systemDefault())
                ?.toLocalDate()
            
            if (recordDate == today) {
                record
            } else {
                // Cached record is not from today, clear it
                Log.d(TAG, "Cached today record is stale (from $recordDate), clearing")
                clearTodayRecord(userId)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached today record", e)
            null
        }
    }
    
    /**
     * Cache today's sleep record
     */
    suspend fun cacheTodayRecord(userId: String, record: FirebaseSleepRecord) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_TODAY_RECORD}_$userId"
            val json = gson.toJson(record)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.SLEEP_DATA, "today_$userId")
            Log.d(TAG, "📦 Cached today's sleep record for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching today record", e)
        }
    }
    
    /**
     * Clear today's record cache
     */
    suspend fun clearTodayRecord(userId: String) = withContext(Dispatchers.IO) {
        val key = "${KEY_TODAY_RECORD}_$userId"
        prefs.edit().remove(key).apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.SLEEP_DATA, "today_$userId")
    }
    
    /**
     * Check if today's record cache is fresh
     */
    fun isTodayRecordCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.SLEEP_DATA,
            "today_$userId",
            TODAY_RECORD_FRESHNESS_MS
        )
    }
    
    // ============ Cache Management ============
    
    /**
     * Check if user has any cached data
     */
    suspend fun hasCachedData(userId: String): Boolean = withContext(Dispatchers.IO) {
        val hasSettings = getCachedSettings(userId) != null
        val hasHistory = getCachedHistory(userId) != null
        hasSettings || hasHistory
    }
    
    /**
     * Invalidate all sleep cache for a user (e.g., after recording new sleep)
     */
    suspend fun invalidateCache(userId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("${KEY_SLEEP_SETTINGS}_$userId")
            .remove("${KEY_SLEEP_HISTORY}_$userId")
            .remove("${KEY_TODAY_RECORD}_$userId")
            .apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.SLEEP_DATA, "settings_$userId")
        CacheManager.invalidateCache(context, CacheManager.DataType.SLEEP_DATA, "history_$userId")
        CacheManager.invalidateCache(context, CacheManager.DataType.SLEEP_DATA, "today_$userId")
        Log.d(TAG, "🗑️ Sleep cache invalidated for user: $userId")
    }
    
    /**
     * Clear all sleep cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.SLEEP_DATA)
        Log.d(TAG, "🗑️ Sleep cache cleared on logout")
    }
}
