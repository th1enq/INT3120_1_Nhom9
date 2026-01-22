package com.example.coupleapp.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.coupleapp.CoupleApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified Cache Manager for CoupleApp
 * 
 * Implements a lazy loading strategy with configurable cache freshness:
 * 1. INSTANT: Show cached data immediately
 * 2. BACKGROUND_REFRESH: If cache is stale, refresh in background (don't block UI)
 * 3. FORCE_REFRESH: Only when user explicitly pulls to refresh
 * 
 * Cache Freshness Policies (based on data volatility):
 * ┌─────────────────────┬──────────────┬─────────────────────────────────────┐
 * │ Data Type           │ Freshness    │ Reason                              │
 * ├─────────────────────┼──────────────┼─────────────────────────────────────┤
 * │ User Profile        │ 30 minutes   │ Rarely changes                      │
 * │ Partner Profile     │ 30 minutes   │ Rarely changes                      │
 * │ Missing Data        │ 5 minutes    │ Updated when sending hearts         │
 * │ Sleep Data          │ 10 minutes   │ Updates at bedtime/wake             │
 * │ Location History    │ 2 minutes    │ Changes when moving                 │
 * │ Current Location    │ 30 seconds   │ Real-time tracking                  │
 * │ Store Inventory     │ 15 minutes   │ Changes on purchase                 │
 * │ Quest Progress      │ 5 minutes    │ Updates on actions                  │
 * │ Garden Data         │ 5 minutes    │ Updates on care actions             │
 * │ Calendar Events     │ 30 minutes   │ User-scheduled events               │
 * │ Locket Photos       │ 5 minutes    │ Partner can send anytime            │
 * │ Chat Messages       │ NO CACHE     │ Real-time (use Firebase listener)   │
 * └─────────────────────┴──────────────┴─────────────────────────────────────┘
 */
object CacheManager {
    
    private const val TAG = "CacheManager"
    private const val PREFS_NAME = "couple_app_cache_metadata"
    
    // Cache freshness durations (in milliseconds)
    object Freshness {
        const val REALTIME = 0L                    // No cache - always fetch
        const val VERY_SHORT = 30 * 1000L          // 30 seconds
        const val SHORT = 2 * 60 * 1000L           // 2 minutes
        const val MEDIUM = 5 * 60 * 1000L          // 5 minutes
        const val LONG = 10 * 60 * 1000L           // 10 minutes
        const val EXTENDED = 15 * 60 * 1000L       // 15 minutes
        const val VERY_LONG = 30 * 60 * 1000L      // 30 minutes
        const val HOUR = 60 * 60 * 1000L           // 1 hour
    }
    
    // Data type keys
    object DataType {
        const val USER_PROFILE = "user_profile"
        const val PARTNER_PROFILE = "partner_profile"
        const val MISSING_DATA = "missing_data"
        const val SLEEP_DATA = "sleep_data"
        const val LOCATION_HISTORY = "location_history"
        const val CURRENT_LOCATION = "current_location"
        const val STORE_INVENTORY = "store_inventory"
        const val STORE_ITEMS = "store_items"
        const val QUEST_PROGRESS = "quest_progress"
        const val GARDEN_DATA = "garden_data"
        const val GARDEN_PLANT = "garden_plant"
        const val GARDEN_INVENTORY = "garden_inventory"
        const val GARDEN_GALLERY = "garden_gallery"
        const val GARDEN_COLLECTION = "garden_collection"
        const val CALENDAR_EVENTS = "calendar_events"
        const val LOCKET_PHOTOS = "locket_photos"
        const val QA_QUESTIONS = "qa_questions"
    }
    
    // Default freshness for each data type
    private val defaultFreshness = mapOf(
        DataType.USER_PROFILE to Freshness.VERY_LONG,
        DataType.PARTNER_PROFILE to Freshness.VERY_LONG,
        DataType.MISSING_DATA to Freshness.MEDIUM,
        DataType.SLEEP_DATA to Freshness.LONG,
        DataType.LOCATION_HISTORY to Freshness.SHORT,
        DataType.CURRENT_LOCATION to Freshness.VERY_SHORT,
        DataType.STORE_INVENTORY to Freshness.EXTENDED,
        DataType.STORE_ITEMS to Freshness.HOUR,
        DataType.QUEST_PROGRESS to Freshness.MEDIUM,
        DataType.GARDEN_DATA to Freshness.MEDIUM,
        DataType.GARDEN_PLANT to Freshness.MEDIUM,
        DataType.GARDEN_INVENTORY to Freshness.EXTENDED,
        DataType.GARDEN_GALLERY to Freshness.HOUR,
        DataType.GARDEN_COLLECTION to Freshness.VERY_LONG,
        DataType.CALENDAR_EVENTS to Freshness.VERY_LONG,
        DataType.LOCKET_PHOTOS to Freshness.MEDIUM,
        DataType.QA_QUESTIONS to Freshness.VERY_LONG
    )
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Record when data was last synced
     */
    fun recordSync(context: Context, dataType: String, identifier: String = "") {
        val key = buildKey(dataType, identifier)
        getPrefs(context).edit()
            .putLong(key, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "📦 Recorded sync for $key")
    }
    
    /**
     * Get last sync time for a data type
     */
    fun getLastSyncTime(context: Context, dataType: String, identifier: String = ""): Long {
        val key = buildKey(dataType, identifier)
        return getPrefs(context).getLong(key, 0L)
    }
    
    /**
     * Check if cached data is still fresh
     */
    fun isCacheFresh(
        context: Context, 
        dataType: String, 
        identifier: String = "",
        customFreshness: Long? = null
    ): Boolean {
        val lastSync = getLastSyncTime(context, dataType, identifier)
        if (lastSync == 0L) return false
        
        val freshnessDuration = customFreshness ?: defaultFreshness[dataType] ?: Freshness.MEDIUM
        val isFresh = (System.currentTimeMillis() - lastSync) < freshnessDuration
        
        Log.d(TAG, "🔍 Cache check for $dataType: fresh=$isFresh, " +
                "age=${(System.currentTimeMillis() - lastSync) / 1000}s, " +
                "threshold=${freshnessDuration / 1000}s")
        
        return isFresh
    }
    
    /**
     * Check if cache exists (regardless of freshness)
     */
    fun hasCachedData(context: Context, dataType: String, identifier: String = ""): Boolean {
        return getLastSyncTime(context, dataType, identifier) > 0
    }
    
    /**
     * Get cache age in seconds
     */
    fun getCacheAgeSeconds(context: Context, dataType: String, identifier: String = ""): Long {
        val lastSync = getLastSyncTime(context, dataType, identifier)
        if (lastSync == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - lastSync) / 1000
    }
    
    /**
     * Invalidate cache for a data type (force refresh on next access)
     */
    fun invalidateCache(context: Context, dataType: String, identifier: String = "") {
        val key = buildKey(dataType, identifier)
        getPrefs(context).edit()
            .remove(key)
            .apply()
        Log.d(TAG, "🗑️ Invalidated cache for $key")
    }
    
    /**
     * Invalidate all cache (e.g., on logout)
     */
    fun invalidateAllCache(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "🗑️ Invalidated ALL cache")
    }
    
    /**
     * Get freshness duration for a data type
     */
    fun getFreshnessDuration(dataType: String): Long {
        return defaultFreshness[dataType] ?: Freshness.MEDIUM
    }
    
    private fun buildKey(dataType: String, identifier: String): String {
        return if (identifier.isEmpty()) {
            "last_sync_$dataType"
        } else {
            "last_sync_${dataType}_$identifier"
        }
    }
}

/**
 * Result wrapper for lazy loaded data
 */
sealed class CacheResult<T> {
    /**
     * Fresh data from cache, no refresh needed
     */
    data class Fresh<T>(val data: T) : CacheResult<T>()
    
    /**
     * Stale data from cache, background refresh triggered
     */
    data class Stale<T>(val data: T, val isRefreshing: Boolean = true) : CacheResult<T>()
    
    /**
     * No cache available, must fetch from network
     */
    class Empty<T> : CacheResult<T>()
    
    /**
     * Error occurred
     */
    data class Error<T>(val message: String, val cachedData: T? = null) : CacheResult<T>()
}

/**
 * Lazy Data Loader - Implements cache-first strategy with background refresh
 * 
 * Usage:
 * ```kotlin
 * val loader = LazyDataLoader<MyData>(
 *     dataType = CacheManager.DataType.MISSING_DATA,
 *     identifier = coupleId,
 *     loadFromCache = { repository.loadFromCache() },
 *     loadFromNetwork = { repository.loadFromNetwork() },
 *     saveToCache = { data -> repository.saveToCache(data) }
 * )
 * 
 * // In ViewModel
 * viewModelScope.launch {
 *     loader.load().collect { result ->
 *         when (result) {
 *             is CacheResult.Fresh -> updateUI(result.data)
 *             is CacheResult.Stale -> updateUI(result.data) // UI updates, refresh in background
 *             is CacheResult.Empty -> showLoading()
 *             is CacheResult.Error -> showError(result.message)
 *         }
 *     }
 * }
 * ```
 */
class LazyDataLoader<T>(
    private val context: Context = CoupleApplication.instance,
    private val dataType: String,
    private val identifier: String = "",
    private val loadFromCache: suspend () -> T?,
    private val loadFromNetwork: suspend () -> T,
    private val saveToCache: suspend (T) -> Unit
) {
    companion object {
        private const val TAG = "LazyDataLoader"
    }
    
    /**
     * Load data with lazy loading strategy:
     * 1. If cache is fresh → return cached data immediately
     * 2. If cache is stale → return cached data + trigger background refresh
     * 3. If no cache → load from network
     */
    suspend fun load(forceRefresh: Boolean = false): CacheResult<T> = withContext(Dispatchers.IO) {
        try {
            // Force refresh - skip cache
            if (forceRefresh) {
                Log.d(TAG, "🔄 Force refresh for $dataType")
                return@withContext loadFromNetworkAndCache()
            }
            
            // Try to load from cache first
            val cachedData = loadFromCache()
            
            if (cachedData != null) {
                val isFresh = CacheManager.isCacheFresh(context, dataType, identifier)
                
                if (isFresh) {
                    Log.d(TAG, "✅ Fresh cache hit for $dataType")
                    return@withContext CacheResult.Fresh(cachedData)
                } else {
                    Log.d(TAG, "📦 Stale cache for $dataType, will refresh in background")
                    // Return stale data immediately, background refresh will happen
                    return@withContext CacheResult.Stale(cachedData, isRefreshing = true)
                }
            } else {
                Log.d(TAG, "❌ No cache for $dataType, loading from network")
                return@withContext CacheResult.Empty()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in lazy load for $dataType", e)
            
            // Try to return cached data even on error
            val fallbackData = try { loadFromCache() } catch (_: Exception) { null }
            return@withContext CacheResult.Error(e.message ?: "Unknown error", fallbackData)
        }
    }
    
    /**
     * Force refresh from network and update cache
     */
    suspend fun refresh(): Result<T> = withContext(Dispatchers.IO) {
        try {
            val data = loadFromNetwork()
            saveToCache(data)
            CacheManager.recordSync(context, dataType, identifier)
            Log.d(TAG, "✅ Refreshed $dataType from network")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing $dataType", e)
            Result.failure(e)
        }
    }
    
    private suspend fun loadFromNetworkAndCache(): CacheResult<T> {
        return try {
            val data = loadFromNetwork()
            saveToCache(data)
            CacheManager.recordSync(context, dataType, identifier)
            CacheResult.Fresh(data)
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: "Network error")
        }
    }
}
