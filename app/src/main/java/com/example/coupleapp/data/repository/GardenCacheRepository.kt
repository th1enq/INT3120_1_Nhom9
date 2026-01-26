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
import java.util.*

/**
 * Repository for caching Garden data.
 * 
 * Cache Strategy:
 * - Plant state: 5 minutes (frequent changes from decay, watering, etc.)
 * - Inventory: 15 minutes (changes on use/purchase)
 * - Gallery: 1 hour (rarely changes - only when new flower is harvested)
 * - Collection: 30 minutes (changes on harvest)
 * 
 * This provides instant UI when opening Garden screen, then real-time sync takes over.
 */
class GardenCacheRepository(
    private val context: Context = CoupleApplication.instance
) {
    
    companion object {
        private const val TAG = "GardenCacheRepo"
        private const val PREFS_NAME = "garden_cache"
        private const val KEY_PLANT = "plant"
        private const val KEY_INVENTORY = "inventory"
        private const val KEY_GALLERY = "gallery"
        private const val KEY_COLLECTION = "collection"
        
        // Cache freshness durations
        const val PLANT_CACHE_FRESHNESS_MS = 5 * 60 * 1000L // 5 minutes
        const val INVENTORY_CACHE_FRESHNESS_MS = 15 * 60 * 1000L // 15 minutes
        const val GALLERY_CACHE_FRESHNESS_MS = 60 * 60 * 1000L // 1 hour
        const val COLLECTION_CACHE_FRESHNESS_MS = 30 * 60 * 1000L // 30 minutes
        
        @Volatile
        private var INSTANCE: GardenCacheRepository? = null
        
        fun getInstance(context: Context = CoupleApplication.instance): GardenCacheRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GardenCacheRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // ============ Plant State ============
    
    /**
     * Get cached plant state
     */
    suspend fun getCachedPlant(userId: String): CachedPlant? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_PLANT}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, CachedPlant::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached plant", e)
            null
        }
    }
    
    /**
     * Cache plant state
     */
    suspend fun cachePlant(userId: String, plant: CachedPlant) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_PLANT}_$userId"
            val json = gson.toJson(plant)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.GARDEN_PLANT, userId)
            Log.d(TAG, "📦 Cached plant: ${plant.plantType} - stage ${plant.stage}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching plant", e)
        }
    }
    
    /**
     * Check if plant cache is fresh
     */
    fun isPlantCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.GARDEN_PLANT,
            userId,
            PLANT_CACHE_FRESHNESS_MS
        )
    }
    
    // ============ Inventory ============
    
    /**
     * Get cached inventory
     */
    suspend fun getCachedInventory(userId: String): CachedGardenInventory? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_INVENTORY}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            gson.fromJson(json, CachedGardenInventory::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached inventory", e)
            null
        }
    }
    
    /**
     * Cache inventory
     */
    suspend fun cacheInventory(userId: String, inventory: CachedGardenInventory) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_INVENTORY}_$userId"
            val json = gson.toJson(inventory)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.GARDEN_INVENTORY, userId)
            Log.d(TAG, "📦 Cached inventory: seeds=${inventory.seeds}, water=${inventory.wateringCan}, sun=${inventory.sunlightBottle}, fert4h=${inventory.fertilizer4h}, fert8h=${inventory.fertilizer8h}, fert12h=${inventory.fertilizer12h}, pesticide=${inventory.pesticide}, scissors=${inventory.scissors}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching inventory", e)
        }
    }
    
    /**
     * Check if inventory cache is fresh
     */
    fun isInventoryCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.GARDEN_INVENTORY,
            userId,
            INVENTORY_CACHE_FRESHNESS_MS
        )
    }
    
    // ============ Gallery ============
    
    /**
     * Get cached gallery
     */
    suspend fun getCachedGallery(userId: String): List<CachedGalleryItem>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_GALLERY}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val type = object : TypeToken<List<CachedGalleryItem>>() {}.type
            gson.fromJson<List<CachedGalleryItem>>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached gallery", e)
            null
        }
    }
    
    /**
     * Cache gallery
     */
    suspend fun cacheGallery(userId: String, gallery: List<CachedGalleryItem>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_GALLERY}_$userId"
            val json = gson.toJson(gallery)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.GARDEN_GALLERY, userId)
            val unlockedCount = gallery.count { it.isUnlocked }
            Log.d(TAG, "📦 Cached gallery: $unlockedCount/${gallery.size} unlocked")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching gallery", e)
        }
    }
    
    /**
     * Check if gallery cache is fresh
     */
    fun isGalleryCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.GARDEN_GALLERY,
            userId,
            GALLERY_CACHE_FRESHNESS_MS
        )
    }
    
    // ============ Collection ============
    
    /**
     * Get cached collection
     */
    suspend fun getCachedCollection(userId: String): List<CachedCollectionPlant>? = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_COLLECTION}_$userId"
            val json = prefs.getString(key, null) ?: return@withContext null
            val type = object : TypeToken<List<CachedCollectionPlant>>() {}.type
            gson.fromJson<List<CachedCollectionPlant>>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached collection", e)
            null
        }
    }
    
    /**
     * Cache collection
     */
    suspend fun cacheCollection(userId: String, collection: List<CachedCollectionPlant>) = withContext(Dispatchers.IO) {
        try {
            val key = "${KEY_COLLECTION}_$userId"
            val json = gson.toJson(collection)
            prefs.edit().putString(key, json).apply()
            CacheManager.recordSync(context, CacheManager.DataType.GARDEN_COLLECTION, userId)
            Log.d(TAG, "📦 Cached collection: ${collection.size} plants")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching collection", e)
        }
    }
    
    /**
     * Check if collection cache is fresh
     */
    fun isCollectionCacheFresh(userId: String): Boolean {
        return CacheManager.isCacheFresh(
            context,
            CacheManager.DataType.GARDEN_COLLECTION,
            userId,
            COLLECTION_CACHE_FRESHNESS_MS
        )
    }
    
    // ============ Utility ============
    
    /**
     * Check if any garden cache exists for user
     */
    fun hasCachedData(userId: String): Boolean {
        val plantKey = "${KEY_PLANT}_$userId"
        val inventoryKey = "${KEY_INVENTORY}_$userId"
        return prefs.contains(plantKey) || prefs.contains(inventoryKey)
    }
    
    /**
     * Invalidate all garden cache for a user
     */
    fun invalidateCache(userId: String) {
        prefs.edit()
            .remove("${KEY_PLANT}_$userId")
            .remove("${KEY_INVENTORY}_$userId")
            .remove("${KEY_GALLERY}_$userId")
            .remove("${KEY_COLLECTION}_$userId")
            .apply()
        Log.d(TAG, "🗑️ Garden cache invalidated for user: $userId")
    }
    
    /**
     * Clear all garden cache (on logout)
     */
    fun clearOnLogout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ All garden cache cleared")
    }
}

// ============ Cached Data Classes ============

/**
 * Cached plant state
 */
data class CachedPlant(
    val id: String,
    val plantType: String,
    val stage: Int,
    val water: Float,
    val sunlight: Float,
    val health: Float,
    val flowerColor: String,
    val rarity: String,
    val fertilizerBoostHours: Int,
    val coupleId: String,
    val lastUpdated: Long
)

/**
 * Cached garden inventory
 * Contains all item types that can be purchased in the Store
 */
data class CachedGardenInventory(
    val seeds: Int = 0,
    val rareSeeds: Int = 0,
    val superRareSeeds: Int = 0,
    val wateringCan: Int = 0,
    val sunlightBottle: Int = 0,
    val fertilizer4h: Int = 0,
    val fertilizer8h: Int = 0,
    val fertilizer12h: Int = 0,
    val pesticide: Int = 0,
    val scissors: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Cached gallery item
 */
data class CachedGalleryItem(
    val id: String,
    val flowerColor: String,
    val rarity: String,
    val plantType: String,
    val isUnlocked: Boolean,
    val unlockedDate: Long?
)

/**
 * Cached collection plant
 */
data class CachedCollectionPlant(
    val id: String,
    val plantType: String,
    val flowerColor: String,
    val rarity: String,
    val harvestedAt: Long
)
