package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.cache.CacheManager
import com.example.coupleapp.data.model.FirebaseCouple
import com.example.coupleapp.data.model.FirebaseUser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for caching User and Partner profile data.
 * 
 * Uses SharedPreferences for lightweight profile caching.
 * Profile data changes infrequently, so 30-minute cache is appropriate.
 * 
 * Cache Strategy:
 * - Load from cache immediately on screen open
 * - Background refresh if cache > 30 minutes old
 * - Force refresh on pull-to-refresh or after profile edit
 */
class ProfileCacheRepository(
    private val context: Context = CoupleApplication.instance,
    private val firestoreRepository: FirebaseFirestoreRepository = FirebaseFirestoreRepository()
) {
    
    companion object {
        private const val TAG = "ProfileCacheRepo"
        private const val PREFS_NAME = "profile_cache"
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_PARTNER_USER = "partner_user"
        private const val KEY_COUPLE_DATA = "couple_data"
        
        @Volatile
        private var INSTANCE: ProfileCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): ProfileCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ Current User Profile ============
    
    /**
     * Get cached current user profile
     */
    suspend fun getCachedCurrentUser(): FirebaseUser? = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_CURRENT_USER, null) ?: return@withContext null
            gson.fromJson(json, FirebaseUser::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached user", e)
            null
        }
    }
    
    /**
     * Cache current user profile
     */
    suspend fun cacheCurrentUser(user: FirebaseUser) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(user)
            prefs.edit().putString(KEY_CURRENT_USER, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.USER_PROFILE, user.id)
            Log.d(TAG, "📦 Cached current user: ${user.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching user", e)
        }
    }
    
    /**
     * Load current user with cache-first strategy
     */
    suspend fun loadCurrentUser(userId: String, forceRefresh: Boolean = false): FirebaseUser? {
        // Check cache first (unless force refresh)
        if (!forceRefresh) {
            val cached = getCachedCurrentUser()
            if (cached != null && cached.id == userId) {
                val isFresh = CacheManager.isCacheFresh(
                    context, 
                    CacheManager.DataType.USER_PROFILE, 
                    userId
                )
                if (isFresh) {
                    Log.d(TAG, "✅ Fresh cache hit for current user")
                    return cached
                } else {
                    Log.d(TAG, "📦 Stale cache for current user, will refresh")
                    // Return cached data, refresh in background will be triggered by caller
                    return cached
                }
            }
        }
        
        // Load from network
        return loadCurrentUserFromNetwork(userId)
    }
    
    /**
     * Load current user from Firestore and cache
     */
    suspend fun loadCurrentUserFromNetwork(userId: String): FirebaseUser? {
        return try {
            val result = firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                userId,
                FirebaseUser::class.java
            )
            result.getOrNull()?.also { user ->
                cacheCurrentUser(user)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user from network", e)
            null
        }
    }
    
    // ============ Partner Profile ============
    
    /**
     * Get cached partner profile
     */
    suspend fun getCachedPartner(): FirebaseUser? = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_PARTNER_USER, null) ?: return@withContext null
            gson.fromJson(json, FirebaseUser::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached partner", e)
            null
        }
    }
    
    /**
     * Cache partner profile
     */
    suspend fun cachePartner(partner: FirebaseUser) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(partner)
            prefs.edit().putString(KEY_PARTNER_USER, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.PARTNER_PROFILE, partner.id)
            Log.d(TAG, "📦 Cached partner: ${partner.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching partner", e)
        }
    }
    
    /**
     * Load partner with cache-first strategy
     */
    suspend fun loadPartner(partnerId: String, forceRefresh: Boolean = false): FirebaseUser? {
        if (!forceRefresh) {
            val cached = getCachedPartner()
            if (cached != null && cached.id == partnerId) {
                val isFresh = CacheManager.isCacheFresh(
                    context,
                    CacheManager.DataType.PARTNER_PROFILE,
                    partnerId
                )
                if (isFresh) {
                    Log.d(TAG, "✅ Fresh cache hit for partner")
                    return cached
                }
                // Return stale cache, caller handles refresh
                return cached
            }
        }
        
        return loadPartnerFromNetwork(partnerId)
    }
    
    /**
     * Load partner from Firestore and cache
     */
    suspend fun loadPartnerFromNetwork(partnerId: String): FirebaseUser? {
        return try {
            val result = firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                partnerId,
                FirebaseUser::class.java
            )
            result.getOrNull()?.also { partner ->
                cachePartner(partner)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading partner from network", e)
            null
        }
    }
    
    // ============ Couple Data ============
    
    /**
     * Get cached couple data
     */
    suspend fun getCachedCouple(): FirebaseCouple? = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_COUPLE_DATA, null) ?: return@withContext null
            gson.fromJson(json, FirebaseCouple::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached couple", e)
            null
        }
    }
    
    /**
     * Cache couple data
     */
    suspend fun cacheCouple(couple: FirebaseCouple) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(couple)
            prefs.edit().putString(KEY_COUPLE_DATA, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.USER_PROFILE, couple.id)
            Log.d(TAG, "📦 Cached couple data: ${couple.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching couple", e)
        }
    }
    
    // ============ Cache Management ============
    
    /**
     * Check if user profile needs refresh
     */
    fun needsRefresh(userId: String): Boolean {
        return !CacheManager.isCacheFresh(context, CacheManager.DataType.USER_PROFILE, userId)
    }
    
    /**
     * Invalidate all profile cache (e.g., after profile edit)
     */
    suspend fun invalidateCache() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.USER_PROFILE)
        CacheManager.invalidateCache(context, CacheManager.DataType.PARTNER_PROFILE)
        Log.d(TAG, "🗑️ Profile cache invalidated")
    }
    
    /**
     * Clear cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Profile cache cleared on logout")
    }
}
