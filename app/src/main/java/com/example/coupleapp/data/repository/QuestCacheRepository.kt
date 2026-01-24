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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for caching Quest data.
 * 
 * Cache Strategy:
 * - Quest progress: 5 minutes (changes on user actions)
 * - User coins: 5 minutes (changes on claim/purchase)
 * - Streak info: 5 minutes (changes daily)
 * 
 * This provides instant UI when opening Quest screen.
 */
class QuestCacheRepository(
    private val context: Context = CoupleApplication.instance
) {
    
    companion object {
        private const val TAG = "QuestCacheRepo"
        private const val PREFS_NAME = "quest_cache"
        private const val KEY_QUEST_DATA = "quest_data"
        private const val KEY_USER_COINS = "user_coins"
        private const val KEY_STREAK_INFO = "streak_info"
        
        // Cache freshness - 5 minutes since quest data changes frequently
        const val CACHE_FRESHNESS_MS = 5 * 60 * 1000L
        
        @Volatile
        private var INSTANCE: QuestCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): QuestCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuestCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // ============ Quest Data ============
    
    /**
     * Get cached quest data for a user
     */
    suspend fun getCachedQuestData(userId: String): CachedQuestData? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_QUEST_DATA}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val cachedData = gson.fromJson(json, CachedQuestData::class.java)
            
            // Check if cached data is from today
            val today = dateFormat.format(Date())
            if (cachedData.cacheDate != today) {
                Log.d(TAG, "Cached quest data is from different day (${cachedData.cacheDate}), ignoring")
                return@withContext null
            }
            
            cachedData
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached quest data", e)
            null
        }
    }
    
    /**
     * Cache quest data
     */
    suspend fun cacheQuestData(userId: String, data: CachedQuestData) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_QUEST_DATA}_$userId"
            val json = gson.toJson(data)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.QUEST_PROGRESS, userId)
            Log.d(TAG, "📦 Cached quest data for user: $userId (${data.quests.size} quests)")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching quest data", e)
        }
    }
    
    /**
     * Check if quest cache is fresh
     */
    fun isQuestCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.QUEST_PROGRESS,
            userId,
            CACHE_FRESHNESS_MS
        )
    }
    
    // ============ User Coins ============
    
    /**
     * Get cached user coins
     */
    suspend fun getCachedCoins(userId: String): Int? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_USER_COINS}_$userId"
            if (prefs.contains(key)) {
                prefs.getInt(key, 0)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached coins", e)
            null
        }
    }
    
    /**
     * Cache user coins
     */
    suspend fun cacheCoins(userId: String, coins: Int) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_USER_COINS}_$userId"
            prefs.edit().putInt(key, coins).apply()
            Log.d(TAG, "📦 Cached $coins coins for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching coins", e)
        }
    }
    
    // ============ Streak Info ============
    
    /**
     * Get cached streak info
     */
    suspend fun getCachedStreakInfo(userId: String): CachedStreakInfo? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_STREAK_INFO}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, CachedStreakInfo::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached streak info", e)
            null
        }
    }
    
    /**
     * Cache streak info
     */
    suspend fun cacheStreakInfo(userId: String, info: CachedStreakInfo) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_STREAK_INFO}_$userId"
            val json = gson.toJson(info)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "📦 Cached streak info for user: $userId (streak=${info.currentStreak})")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching streak info", e)
        }
    }
    
    // ============ Cache Management ============
    
    /**
     * Check if any cached data exists for user
     */
    suspend fun hasCachedData(userId: String): Boolean = withContext(Dispatchers.IO) {
        val hasQuests = getCachedQuestData(userId) != null
        val hasCoins = getCachedCoins(userId) != null
        hasQuests || hasCoins
    }
    
    /**
     * Invalidate quest cache (e.g., after claiming reward)
     */
    suspend fun invalidateCache(userId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("${KEY_QUEST_DATA}_$userId")
            .remove("${KEY_USER_COINS}_$userId")
            .remove("${KEY_STREAK_INFO}_$userId")
            .apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.QUEST_PROGRESS, userId)
        Log.d(TAG, "🗑️ Quest cache invalidated for user: $userId")
    }
    
    /**
     * Clear all quest cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.QUEST_PROGRESS)
        Log.d(TAG, "🗑️ Quest cache cleared on logout")
    }
}

/**
 * Cacheable quest data container
 */
data class CachedQuestData(
    val cacheDate: String, // yyyy-MM-dd format - must match current date
    val quests: List<CachedQuest>,
    val specialQuest: CachedQuest?,
    val dailySummary: CachedDailySummary,
    val isLinkedWithPartner: Boolean
)

/**
 * Cacheable quest
 */
data class CachedQuest(
    val id: String,
    val title: String,
    val vietnameseTitle: String = "",
    val description: String,
    val vietnameseDescription: String = "",
    val iconRes: Int,
    val rewardCoins: Int,
    val targetProgress: Int,
    val currentProgress: Int,
    val status: String, // QuestStatus name
    val type: String, // QuestType name
    val isSpecial: Boolean = false,
    val navigationRoute: String = ""
)

/**
 * Cacheable daily summary
 */
data class CachedDailySummary(
    val completedQuests: Int,
    val totalQuests: Int,
    val claimedQuests: Int,
    val totalCoinsEarned: Int,
    val totalCoinsAvailable: Int,
    val bonusRewardUnlocked: Boolean
)

/**
 * Cacheable streak info
 */
data class CachedStreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val missedDays: Int,
    val lastClaimDate: String?
)
