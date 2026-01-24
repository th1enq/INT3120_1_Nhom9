package com.example.coupleapp.data.repository

import android.content.Context
import android.util.Log
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.cache.CacheManager
import com.example.coupleapp.data.model.FirebaseUserWallet
import com.example.coupleapp.data.model.UserWallet
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for caching Store data (user wallet, inventory).
 * 
 * Cache Strategy:
 * - User wallet: 15 minutes freshness (changes on purchase)
 * - Force refresh after any purchase
 * - Categories are static, no need to cache
 */
class StoreCacheRepository(
    private val context: Context = CoupleApplication.instance,
    private val firestoreRepository: FirebaseFirestoreRepository = FirebaseFirestoreRepository()
) {
    
    companion object {
        private const val TAG = "StoreCacheRepo"
        private const val PREFS_NAME = "store_cache"
        private const val KEY_USER_WALLET = "user_wallet"
        private const val KEY_INVENTORY = "inventory"
        
        @Volatile
        private var INSTANCE: StoreCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): StoreCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StoreCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ User Wallet ============
    
    /**
     * Get cached user wallet
     */
    suspend fun getCachedWallet(userId: String): UserWallet? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_USER_WALLET}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, UserWallet::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached wallet", e)
            null
        }
    }
    
    /**
     * Cache user wallet
     */
    suspend fun cacheWallet(userId: String, wallet: UserWallet) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_USER_WALLET}_$userId"
            val json = gson.toJson(wallet)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.STORE_INVENTORY, userId)
            Log.d(TAG, "📦 Cached wallet: ${wallet.coins} coins")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching wallet", e)
        }
    }
    
    /**
     * Load wallet with cache-first strategy
     * 
     * @param userId User ID
     * @param forceRefresh Force network fetch
     * @return Pair of (wallet, needsBackgroundRefresh)
     */
    suspend fun loadWallet(userId: String, forceRefresh: Boolean = false): Pair<UserWallet?, Boolean> {
        if (!forceRefresh) {
            val cached = getCachedWallet(userId)
            if (cached != null) {
                val isFresh = CacheManager.isCacheFresh(
                    context,
                    CacheManager.DataType.STORE_INVENTORY,
                    userId
                )
                if (isFresh) {
                    Log.d(TAG, "✅ Fresh cache hit for wallet")
                    return Pair(cached, false)
                } else {
                    Log.d(TAG, "📦 Stale cache for wallet, needs refresh")
                    return Pair(cached, true) // Return cached, flag for refresh
                }
            }
        }
        
        // No cache or force refresh - load from network
        val wallet = loadWalletFromNetwork(userId)
        return Pair(wallet, false)
    }
    
    /**
     * Load wallet from Firestore and cache
     */
    suspend fun loadWalletFromNetwork(userId: String): UserWallet? {
        return try {
            val result = firestoreRepository.getDocument(
                "user_wallets",
                userId,
                FirebaseUserWallet::class.java
            )
            
            result.fold(
                onSuccess = { walletDoc ->
                    if (walletDoc != null) {
                        val wallet = UserWallet(
                            coins = walletDoc.coins,
                            lastFreeClaimTime = walletDoc.lastFreeGiftDate?.let {
                                java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                    .atStartOfDay()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            }
                        )
                        cacheWallet(userId, wallet)
                        wallet
                    } else {
                        Log.d(TAG, "No wallet found, creating default")
                        // Could create default wallet here
                        val defaultWallet = UserWallet(coins = 1000)
                        cacheWallet(userId, defaultWallet)
                        defaultWallet
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading wallet from network", e)
                    null
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading wallet from network", e)
            null
        }
    }
    
    // ============ Inventory ============
    
    /**
     * Get cached inventory
     */
    suspend fun getCachedInventory(userId: String): List<InventoryItem>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_INVENTORY}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val type = object : com.google.gson.reflect.TypeToken<List<InventoryItem>>() {}.type
            gson.fromJson<List<InventoryItem>>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached inventory", e)
            null
        }
    }
    
    /**
     * Cache inventory
     */
    suspend fun cacheInventory(userId: String, inventory: List<InventoryItem>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_INVENTORY}_$userId"
            val json = gson.toJson(inventory)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "📦 Cached ${inventory.size} inventory items")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching inventory", e)
        }
    }
    
    // ============ Cache Management ============
    
    /**
     * Invalidate store cache (e.g., after purchase)
     */
    suspend fun invalidateCache(userId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("${KEY_USER_WALLET}_$userId")
            .remove("${KEY_INVENTORY}_$userId")
            .apply()
        CacheManager.invalidateCache(context, CacheManager.DataType.STORE_INVENTORY)
        Log.d(TAG, "🗑️ Store cache invalidated")
    }
    
    /**
     * Clear all store cache on logout
     */
    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Store cache cleared on logout")
    }
}

// NOTE: FirebaseUserWallet is imported from FirebaseModels.kt

/**
 * Inventory item model
 */
data class InventoryItem(
    val itemId: String,
    val quantity: Int,
    val purchasedAt: Long? = null
)
