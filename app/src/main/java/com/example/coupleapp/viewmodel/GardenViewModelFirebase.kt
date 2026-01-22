package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.data.repository.GardenCacheRepository
import com.example.coupleapp.data.repository.CachedPlant
import com.example.coupleapp.data.repository.CachedGardenInventory
import com.example.coupleapp.data.repository.CachedGalleryItem
import com.example.coupleapp.data.repository.CachedCollectionPlant
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import kotlin.random.Random

/**
 * Firebase-integrated ViewModel for Garden Screen
 * Manages plant state, inventory from Store purchases, and gallery
 * 
 * Uses Cache-First Strategy:
 * 1. On init: Load cached data immediately (instant UI)
 * 2. Background refresh: Load fresh data from Firebase
 * 3. Real-time sync: Enabled when user is viewing Garden screen
 * 
 * Cache freshness:
 * - Plant: 5 minutes (frequent changes)
 * - Inventory: 15 minutes (changes on use/purchase)
 * - Gallery: 1 hour (rarely changes)
 * - Collection: 30 minutes (changes on harvest)
 */
class GardenViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val gardenCache = GardenCacheRepository.getInstance()

    private val _uiState = MutableStateFlow(GardenUiState())
    val uiState: StateFlow<GardenUiState> = _uiState.asStateFlow()

    private var statusDecayJob: Job? = null
    private var thoughtBubbleJob: Job? = null
    
    // Real-time listener for plant updates (only active when user is viewing garden)
    private var plantListener: ListenerRegistration? = null
    private var currentPlantId: String? = null
    private var isRealTimeEnabled = false

    init {
        loadGardenDataWithCache()
        startStatusDecay()
        startThoughtBubbleCheck()
    }
    
    /**
     * Load garden data with cache-first strategy
     */
    private fun loadGardenDataWithCache() {
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Bạn chưa đăng nhập")
                }
                return@launch
            }
            
            // Try to load from cache first
            val hasCached = gardenCache.hasCachedData(userId)
            
            if (hasCached) {
                Log.d("GardenViewModel", "📦 Cache found! Loading from cache first...")
                
                try {
                    val cachedPlant = gardenCache.getCachedPlant(userId)
                    val cachedInventory = gardenCache.getCachedInventory(userId)
                    val cachedGallery = gardenCache.getCachedGallery(userId)
                    val cachedCollection = gardenCache.getCachedCollection(userId)
                    
                    // Show cached data immediately
                    val plant = cachedPlant?.toPlant()
                    val inventory = cachedInventory?.toGardenInventory() ?: createDefaultInventory()
                    val gallery = cachedGallery?.map { it.toGalleryPlant() } ?: createDefaultGallery()
                    val collection = cachedCollection?.map { it.toCollectedPlant() } ?: emptyList()
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            plant = plant,
                            inventory = inventory,
                            gallery = gallery,
                            collection = collection,
                            coupleId = cachedPlant?.coupleId ?: ""
                        )
                    }
                    Log.d("GardenViewModel", "✅ UI updated from cache")
                    
                    // Refresh in background if cache is stale
                    val isPlantFresh = gardenCache.isPlantCacheFresh(userId)
                    val isInventoryFresh = gardenCache.isInventoryCacheFresh(userId)
                    
                    if (!isPlantFresh || !isInventoryFresh) {
                        Log.d("GardenViewModel", "🔄 Cache is stale, refreshing in background...")
                        loadGardenData(showLoading = false)
                    }
                    
                    // Setup real-time listener if enabled
                    if (plant != null && isRealTimeEnabled) {
                        setupPlantListener(plant.id)
                    }
                    
                } catch (e: Exception) {
                    Log.e("GardenViewModel", "Error loading from cache", e)
                    loadGardenData(showLoading = true)
                }
            } else {
                Log.d("GardenViewModel", "🌐 No cache, loading from Firebase...")
                loadGardenData(showLoading = true)
            }
        }
    }
    
    /**
     * Enable real-time sync when user enters Garden screen
     * This saves battery and data by only listening when needed
     */
    fun enableRealTimeSync() {
        if (isRealTimeEnabled) return
        isRealTimeEnabled = true
        
        val plantId = _uiState.value.plant?.id
        if (plantId != null) {
            Log.d("GardenViewModel", "[GARDEN] 🔔 Enabling real-time sync for plant: $plantId")
            setupPlantListener(plantId)
        }
    }
    
    /**
     * Disable real-time sync when user leaves Garden screen
     * This saves battery and data
     */
    fun disableRealTimeSync() {
        if (!isRealTimeEnabled) return
        isRealTimeEnabled = false
        
        Log.d("GardenViewModel", "[GARDEN] 🔕 Disabling real-time sync")
        plantListener?.remove()
        plantListener = null
        currentPlantId = null
    }

    /**
     * Load garden data from Firebase
     * @param showLoading Whether to show loading indicator (false for background refresh)
     */
    private fun loadGardenData(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Bạn chưa đăng nhập"
                    )
                }
                return@launch
            }

            try {
                // Load user to get coupleId
                Log.d("GardenViewModel", "[GARDEN] ▶️ Loading garden for userId: $userId")
                val userResult = firestoreRepository.getDocument(
                    collection = "users",
                    documentId = userId,
                    clazz = FirebaseUser::class.java
                )

                val userData = userResult.getOrNull()
                val coupleId = userData?.coupleId
                val partnerId = userData?.partnerId
                Log.d("GardenViewModel", "[GARDEN] User data: coupleId=$coupleId, partnerId=$partnerId")

                // Load plant - if user has coupleId, load shared plant, otherwise load by userId
                val plantResult = if (!coupleId.isNullOrEmpty()) {
                    Log.d("GardenViewModel", "[GARDEN] Loading shared plant by coupleId: $coupleId")
                    firestoreRepository.queryDocuments(
                        collection = "garden_plants",
                        field = "coupleId",
                        value = coupleId,
                        clazz = FirebaseGardenPlant::class.java
                    )
                } else {
                    Log.d("GardenViewModel", "[GARDEN] Loading personal plant by userId: $userId")
                    firestoreRepository.queryDocuments(
                        collection = "garden_plants",
                        field = "userId",
                        value = userId,
                        clazz = FirebaseGardenPlant::class.java
                    )
                }

                // Load inventory from Store purchases
                val inventoryResult = firestoreRepository.getDocument(
                    collection = "garden_inventories",
                    documentId = userId,
                    clazz = FirebaseGardenInventory::class.java
                )

                // Load gallery - shared between couple
                val galleryResult = if (!coupleId.isNullOrEmpty()) {
                    firestoreRepository.queryDocuments(
                        collection = "garden_gallery",
                        field = "coupleId",
                        value = coupleId,
                        clazz = FirebaseGalleryItem::class.java
                    )
                } else {
                    firestoreRepository.queryDocuments(
                        collection = "garden_gallery",
                        field = "userId",
                        value = userId,
                        clazz = FirebaseGalleryItem::class.java
                    )
                }

                plantResult.fold(
                    onSuccess = { plants ->
                        Log.d("GardenViewModel", "[GARDEN] ✅ Found ${plants.size} plant(s) in Firebase")
                        plants.forEachIndexed { index, fbPlant ->
                            Log.d("GardenViewModel", "[GARDEN]   Plant #$index: id=${fbPlant.id}, coupleId=${fbPlant.coupleId}, userId=${fbPlant.userId}, stage=${fbPlant.stage}, type=${fbPlant.plantType}, water=${fbPlant.water}, sun=${fbPlant.sunlight}, health=${fbPlant.health}")
                        }
                        val plant = plants.firstOrNull()?.toPlant()
                        if (plant != null) {
                            Log.d("GardenViewModel", "[GARDEN] ✓ Using plant: id=${plant.id}, stage=${plant.stage}, type=${plant.plantType}, boost=${plant.fertilizerBoostHours}h")
                        } else {
                            Log.d("GardenViewModel", "[GARDEN] ⚠️ No plant found - user needs to plant seed")
                        }
                        
                        // Load collection (shared between couple)
                        val collection = if (!coupleId.isNullOrEmpty()) {
                            loadCollection(coupleId)
                        } else emptyList()
                        Log.d("GardenViewModel", "[GARDEN] Loaded ${collection.size} plants in collection")
                        
                        inventoryResult.fold(
                            onSuccess = { firebaseInventory ->
                                val inventory = firebaseInventory?.toGardenInventory() 
                                    ?: createDefaultInventory()
                                
                                galleryResult.fold(
                                    onSuccess = { galleryItems ->
                                        Log.d("GardenViewModel", "[GARDEN] 📚 Loaded ${galleryItems.size} gallery items from Firebase")
                                        galleryItems.forEach { item ->
                                            Log.d("GardenViewModel", "[GARDEN]   Gallery item: color=${item.flowerColor}, rarity=${item.rarity}, type=${item.plantType}, unlocked=${item.isUnlocked}")
                                        }
                                        val gallery = createGalleryWithUnlocks(galleryItems)
                                        val unlockedCount = gallery.count { it.isUnlocked }
                                        Log.d("GardenViewModel", "[GARDEN] 📚 Gallery created: $unlockedCount / ${gallery.size} unlocked")
                                        
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                plant = plant,
                                                inventory = inventory,
                                                gallery = gallery,
                                                collection = collection,
                                                coupleId = coupleId ?: "",
                                                partnerId = partnerId ?: ""
                                            )
                                        }
                                        
                                        // Cache the loaded data
                                        cacheGardenData(userId, plant, firebaseInventory, galleryItems, collection)
                                        
                                        // Check for pending evolution and decay after loading
                                        if (plant != null) {
                                            // Apply any pending status decay based on time elapsed
                                            applyPendingStatusDecay(plant)
                                            // Check if plant should evolve
                                            checkPlantEvolution()
                                            // Only setup listener if real-time sync is enabled
                                            if (isRealTimeEnabled) {
                                                setupPlantListener(plant.id)
                                            }
                                        }
                                    },
                                    onFailure = { error ->
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                plant = plant,
                                                inventory = inventory,
                                                gallery = createDefaultGallery(),
                                                errorMessage = "Không thể tải gallery: ${error.message}"
                                            )
                                        }
                                    }
                                )
                            },
                            onFailure = { error ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = "Không thể tải inventory: ${error.message}"
                                    )
                                }
                            }
                        )
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Không thể tải dữ liệu vườn: ${error.message}"
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Lỗi: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Convert Firebase plant to domain model
     */
    private fun FirebaseGardenPlant.toPlant(): Plant {
        android.util.Log.d("GardenViewModel", "[GARDEN] 🌱 Converting Firebase plant to domain model:")
        android.util.Log.d("GardenViewModel", "[GARDEN]    id=${this.id}, coupleId=${this.coupleId}, userId=${this.userId}")
        android.util.Log.d("GardenViewModel", "[GARDEN]    stage=${this.stage}, rarity=${this.rarity}, color=${this.flowerColor}")
        android.util.Log.d("GardenViewModel", "[GARDEN]    plantType=${this.plantType}, fertilizerBoost=${this.fertilizerBoostHours}h")
        android.util.Log.d("GardenViewModel", "[GARDEN]    water=${this.water}, sunlight=${this.sunlight}, health=${this.health}")
        val stage = when (this.stage.lowercase()) {
            "seed" -> PlantStage.SEED
            "sprout" -> PlantStage.SPROUT
            "seedling" -> PlantStage.SEEDLING
            "growing" -> PlantStage.GROWING
            "mature" -> PlantStage.MATURE
            "blooming" -> PlantStage.BLOOMING
            else -> PlantStage.SEED
        }

        val rarity = when (this.rarity.lowercase()) {
            "uncommon" -> PlantRarity.UNCOMMON
            "rare" -> PlantRarity.RARE
            "super_rare" -> PlantRarity.SUPER_RARE
            else -> PlantRarity.COMMON
        }

        val flowerColor = PlantFlowerColor.values().find { 
            it.name.lowercase() == this.flowerColor.lowercase() 
        } ?: PlantFlowerColor.PINK

        val plantType = PlantType.values().find {
            it.name.lowercase() == this.plantType.lowercase()
        } ?: PlantType.ROSE

        val plant = Plant(
            id = this.id,
            name = this.plantName,
            stage = stage,
            status = PlantStatus(
                sunlight = this.sunlight,
                water = this.water,
                health = this.health,
                lastUpdateTime = this.updatedAt?.time ?: System.currentTimeMillis()
            ),
            rarity = rarity,
            plantType = plantType,
            flowerColor = flowerColor,
            isInGreenhouse = this.isInGreenhouse,
            stageStartedAt = this.stageStartedAt,
            fertilizerBoostHours = this.fertilizerBoostHours,
            coupleId = this.coupleId,
            plantedByUserId = this.plantedByUserId,
            lastCaredByUserId = this.lastCaredByUserId,
            plantedAt = this.createdAt?.time ?: System.currentTimeMillis()
        )
        android.util.Log.d("GardenViewModel", "[GARDEN] ✓ Plant converted successfully: ${plant.id}")
        return plant
    }

    /**
     * Convert Firebase inventory to domain model
     * Always includes ALL item types, even with 0 quantity, to prevent fallback to default inventory
     */
    private fun FirebaseGardenInventory.toGardenInventory(): GardenInventory {
        Log.d("GardenViewModel", "[GARDEN] Converting Firebase inventory to domain: seeds=${this.seeds}, rareSeeds=${this.rareSeeds}, superRareSeeds=${this.superRareSeeds}, fert4h=${this.fertilizer4h}, fert8h=${this.fertilizer8h}, fert12h=${this.fertilizer12h}, water=${this.wateringCan}, sun=${this.sunlightBottle}, pesticide=${this.pesticide}, scissors=${this.scissors}")

        // Always include ALL item types to prevent items from "disappearing" when quantity is 0
        val items = mapOf(
            CareItemType.SEED_NORMAL to createDefaultItem(CareItemType.SEED_NORMAL).copy(quantity = this.seeds),
            CareItemType.SEED_RARE to createDefaultItem(CareItemType.SEED_RARE).copy(quantity = this.rareSeeds),
            CareItemType.SEED_SUPER_RARE to createDefaultItem(CareItemType.SEED_SUPER_RARE).copy(quantity = this.superRareSeeds),
            CareItemType.FERTILIZER_4H to createDefaultItem(CareItemType.FERTILIZER_4H).copy(quantity = this.fertilizer4h),
            CareItemType.FERTILIZER_8H to createDefaultItem(CareItemType.FERTILIZER_8H).copy(quantity = this.fertilizer8h),
            CareItemType.FERTILIZER_24H to createDefaultItem(CareItemType.FERTILIZER_24H).copy(quantity = this.fertilizer12h),
            CareItemType.WATER to createDefaultItem(CareItemType.WATER).copy(quantity = this.wateringCan),
            CareItemType.SUNLIGHT to createDefaultItem(CareItemType.SUNLIGHT).copy(quantity = this.sunlightBottle),
            CareItemType.PESTICIDE to createDefaultItem(CareItemType.PESTICIDE).copy(quantity = this.pesticide),
            CareItemType.SCISSORS to createDefaultItem(CareItemType.SCISSORS).copy(quantity = this.scissors)
        )
        
        Log.d("GardenViewModel", "[GARDEN] Final inventory has ${items.size} item types (all types included)")

        return GardenInventory(
            items = items,
            lastUpdated = this.updatedAt?.time ?: System.currentTimeMillis()
        )
    }

    /**
     * Create gallery with unlocked items
     */
    private fun createGalleryWithUnlocks(galleryItems: List<FirebaseGalleryItem>): List<GalleryPlant> {
        // Convert to lowercase for consistent matching
        val unlockedSet = galleryItems.map { 
            "${it.flowerColor.lowercase()}_${it.rarity.lowercase()}_${it.plantType.lowercase()}" 
        }.toSet()
        Log.d("GardenViewModel", "[GALLERY] Unlocked set keys: $unlockedSet")
        
        return PlantFlowerColor.values().flatMap { color ->
            PlantRarity.values().flatMap { rarity ->
                PlantType.values().map { plantType ->
                    val id = "${color.name}_${rarity.name}_${plantType.name}"
                    // Use lowercase for matching
                    val matchKey = "${color.name.lowercase()}_${rarity.name.lowercase()}_${plantType.name.lowercase()}"
                    val isUnlocked = matchKey in unlockedSet
                    val unlockedItem = galleryItems.find { 
                        "${it.flowerColor.lowercase()}_${it.rarity.lowercase()}_${it.plantType.lowercase()}" == matchKey 
                    }
                    
                    GalleryPlant(
                        id = id,
                        flowerColor = color,
                        rarity = rarity,
                        plantType = plantType,
                        isUnlocked = isUnlocked,
                        unlockedAt = unlockedItem?.unlockedAt?.toDate()?.time
                    )
                }
            }
        }
    }

    /**
     * Create default gallery
     */
    private fun createDefaultGallery(): List<GalleryPlant> {
        return PlantFlowerColor.values().flatMap { color ->
            PlantRarity.values().flatMap { rarity ->
                PlantType.values().map { plantType ->
                    GalleryPlant(
                        id = "${color.name}_${rarity.name}_${plantType.name}",
                        flowerColor = color,
                        rarity = rarity,
                        plantType = plantType,
                        isUnlocked = false
                    )
                }
            }
        }
    }

    /**
     * Setup real-time listener for plant updates from partner
     * This ensures when partner fertilizes/waters, our UI updates immediately
     */
    private fun setupPlantListener(plantId: String) {
        // Don't re-register if already listening to this plant
        if (plantId == currentPlantId && plantListener != null) {
            Log.d("GardenViewModel", "[GARDEN] Already listening to plant: $plantId")
            return
        }
        
        // Remove previous listener if exists
        plantListener?.remove()
        currentPlantId = plantId
        
        Log.d("GardenViewModel", "[GARDEN] 👂 Setting up real-time listener for plant: $plantId")
        
        plantListener = Firebase.firestore.collection("garden_plants")
            .document(plantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GardenViewModel", "[GARDEN] Error listening to plant", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val firebasePlant = snapshot.toObject(FirebaseGardenPlant::class.java)
                        if (firebasePlant != null) {
                            val currentUserId = authRepository.currentUser?.uid ?: ""
                            val currentPlant = _uiState.value.plant
                            
                            // Only update if the change came from partner (different lastCaredByUserId)
                            // or if fertilizer boost changed
                            val isPartnerUpdate = firebasePlant.lastCaredByUserId != currentUserId && 
                                                  firebasePlant.lastCaredByUserId.isNotEmpty()
                            val hasBoostChange = currentPlant?.fertilizerBoostHours != firebasePlant.fertilizerBoostHours
                            val hasStatusChange = currentPlant?.status?.water != firebasePlant.water ||
                                                  currentPlant?.status?.sunlight != firebasePlant.sunlight ||
                                                  currentPlant?.status?.health != firebasePlant.health
                            
                            if (isPartnerUpdate || hasBoostChange || hasStatusChange) {
                                Log.d("GardenViewModel", "[GARDEN] 🔄 Received real-time update: " +
                                        "water=${firebasePlant.water}, sun=${firebasePlant.sunlight}, " +
                                        "health=${firebasePlant.health}, boost=${firebasePlant.fertilizerBoostHours}h, " +
                                        "lastCaredBy=${firebasePlant.lastCaredByUserId}")
                                
                                val updatedPlant = firebasePlant.toPlant()
                                _uiState.update { state ->
                                    state.copy(
                                        plant = updatedPlant,
                                        lastPartnerCareTime = if (isPartnerUpdate) System.currentTimeMillis() else state.lastPartnerCareTime
                                    )
                                }
                                
                                // Check if plant can evolve now
                                checkPlantEvolution()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GardenViewModel", "[GARDEN] Error parsing plant update", e)
                    }
                } else if (snapshot != null && !snapshot.exists()) {
                    // Plant was deleted (harvested or died)
                    Log.d("GardenViewModel", "[GARDEN] Plant document deleted")
                    _uiState.update { it.copy(plant = null) }
                    plantListener?.remove()
                    plantListener = null
                    currentPlantId = null
                }
            }
    }

    /**
     * Start periodic status decay
     */
    private fun startStatusDecay() {
        statusDecayJob?.cancel()
        statusDecayJob = viewModelScope.launch {
            while (true) {
                delay(60_000) // Check every minute
                decayPlantStatus()
                // Also check if plant can evolve to next stage naturally
                checkPlantEvolution()
            }
        }
    }

    /**
     * Apply pending status decay when app starts (based on time since last update)
     * This handles the case when user has been away from the app for a while
     */
    private fun applyPendingStatusDecay(plant: Plant) {
        if (plant.status.isDead) return
        
        val decayRate = if (plant.isInGreenhouse) 0.5f else 1f
        val timeSinceUpdate = System.currentTimeMillis() - plant.status.lastUpdateTime
        val hoursElapsed = timeSinceUpdate / 3600000f
        
        // Only apply decay if more than 1 minute has passed
        if (hoursElapsed < 0.016f) return
        
        val decayAmount = hoursElapsed * 3f * decayRate // 3% per hour decay rate
        
        Log.d("GardenViewModel", "[GARDEN] Applying pending decay: ${hoursElapsed}h elapsed, decay=$decayAmount")

        val newStatus = plant.status.copy(
            sunlight = (plant.status.sunlight - decayAmount).coerceIn(0f, 100f),
            water = (plant.status.water - decayAmount * 1.2f).coerceIn(0f, 100f),
            health = (plant.status.health - decayAmount * 0.8f).coerceIn(0f, 100f),
            lastUpdateTime = System.currentTimeMillis()
        )

        val updatedPlant = plant.copy(status = newStatus)
        
        _uiState.update {
            it.copy(plant = updatedPlant)
        }

        // Save to Firebase
        savePlantStatus(updatedPlant)

        if (newStatus.isDead) {
            onPlantDied()
        }
    }

    /**
     * Decay plant status over time
     */
    private fun decayPlantStatus() {
        val plant = _uiState.value.plant ?: return
        if (plant.status.isDead) return

        val decayRate = if (plant.isInGreenhouse) 0.5f else 1f
        val timeSinceUpdate = System.currentTimeMillis() - plant.status.lastUpdateTime
        // Decay based on time since last update (about 2-3% per hour for normal plants)
        val hoursElapsed = timeSinceUpdate / 3600000f
        val decayAmount = hoursElapsed * 3f * decayRate // 3% per hour decay rate

        val newStatus = plant.status.copy(
            sunlight = (plant.status.sunlight - decayAmount).coerceIn(0f, 100f),
            water = (plant.status.water - decayAmount * 1.2f).coerceIn(0f, 100f),
            health = (plant.status.health - decayAmount * 0.8f).coerceIn(0f, 100f),
            lastUpdateTime = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(plant = plant.copy(status = newStatus))
        }

        // Save to Firebase
        savePlantStatus(plant.copy(status = newStatus))

        if (newStatus.isDead) {
            onPlantDied()
        }
    }

    /**
     * Start thought bubble checker
     */
    private fun startThoughtBubbleCheck() {
        thoughtBubbleJob?.cancel()
        thoughtBubbleJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                checkPlantThought()
            }
        }
    }

    /**
     * Check if plant needs something
     */
    private fun checkPlantThought() {
        val plant = _uiState.value.plant ?: return
        if (plant.status.isDead) {
            _uiState.update { it.copy(plantThought = null) }
            return
        }

        val mostNeeded = plant.status.mostNeededStatus
        if (mostNeeded != null && 
            ((mostNeeded == PlantStatusType.SUNLIGHT && plant.status.needsSunlight) ||
             (mostNeeded == PlantStatusType.WATER && plant.status.needsWater) ||
             (mostNeeded == PlantStatusType.HEALTH && plant.status.needsHealth))) {
            _uiState.update { it.copy(plantThought = mostNeeded) }
        } else {
            _uiState.update { it.copy(plantThought = null) }
        }
    }

    /**
     * Handle plant death
     */
    private fun onPlantDied() {
        viewModelScope.launch {
            val plant = _uiState.value.plant ?: return@launch
            
            // Remove plant from Firebase
            firestoreRepository.deleteDocument(
                collection = "garden_plants",
                documentId = plant.id
            )

            _uiState.update {
                it.copy(
                    plant = null,
                    errorMessage = "Cây của bạn đã chết! Hãy trồng cây mới."
                )
            }
        }
    }

    /**
     * Use care item on plant
     */
    fun useCareItem(itemType: CareItemType) {
        val plant = _uiState.value.plant ?: return
        val inventory = _uiState.value.inventory
        val userId = authRepository.currentUser?.uid ?: return
        
        if (!inventory.hasItem(itemType)) {
            _uiState.update { it.copy(errorMessage = "Bạn không có vật phẩm này!") }
            return
        }

        val item = inventory.getItem(itemType) ?: return

        viewModelScope.launch {
            // Start animation
            _uiState.update {
                it.copy(
                    showItemAnimation = true,
                    animatingItem = item
                )
            }

            delay(2000)

            // Handle fertilizer separately - it modifies fertilizerBoostHours, not status
            val isFertilizer = itemType in listOf(
                CareItemType.FERTILIZER_4H, 
                CareItemType.FERTILIZER_8H, 
                CareItemType.FERTILIZER_24H
            )
            
            if (isFertilizer) {
                // Apply fertilizer boost
                val newBoostHours = plant.fertilizerBoostHours + item.boostHours
                val fertilizedPlant = plant.copy(
                    fertilizerBoostHours = newBoostHours,
                    lastCaredByUserId = userId
                )
                val updatedInventory = inventory.useItem(itemType)
                
                Log.d("GardenViewModel", "[GARDEN] 🌱 Applied fertilizer: +${item.boostHours}h boost, total=${newBoostHours}h")
                Log.d("GardenViewModel", "[GARDEN] 🌱 Plant canEvolve=${fertilizedPlant.canEvolve}, timeToNextStage=${fertilizedPlant.timeToNextStage}ms")

                // Update UI with fertilized plant
                _uiState.update {
                    it.copy(
                        plant = fertilizedPlant,
                        inventory = updatedInventory,
                        showItemAnimation = false,
                        animatingItem = null,
                        lastPartnerCareTime = if (userId != plant.plantedByUserId) System.currentTimeMillis() else it.lastPartnerCareTime
                    )
                }

                // Save fertilizer boost to Firebase
                firestoreRepository.updateDocument(
                    collection = "garden_plants",
                    documentId = plant.id,
                    updates = mapOf(
                        "fertilizerBoostHours" to newBoostHours,
                        "lastCaredByUserId" to userId,
                        "updatedAt" to Timestamp(Date())
                    )
                )
                saveInventory(updatedInventory)
                
                // Check if plant can evolve now
                checkPlantEvolution()
                return@launch
            }

            // Apply effect for non-fertilizer items
            val newStatus = when (itemType) {
                CareItemType.WATER -> plant.status.copy(
                    water = (plant.status.water + item.effectValue).coerceIn(0f, 100f),
                    lastUpdateTime = System.currentTimeMillis()
                )
                CareItemType.SUNLIGHT -> plant.status.copy(
                    sunlight = (plant.status.sunlight + item.effectValue).coerceIn(0f, 100f),
                    lastUpdateTime = System.currentTimeMillis()
                )
                CareItemType.PESTICIDE, CareItemType.SCISSORS -> plant.status.copy(
                    health = (plant.status.health + item.effectValue).coerceIn(0f, 100f),
                    lastUpdateTime = System.currentTimeMillis()
                )
                else -> plant.status
            }

            // Update lastCaredByUserId to track partner collaboration
            val updatedPlant = plant.copy(
                status = newStatus,
                lastCaredByUserId = userId
            )
            val updatedInventory = inventory.useItem(itemType)

            // Update UI
            _uiState.update {
                it.copy(
                    plant = updatedPlant,
                    inventory = updatedInventory,
                    showItemAnimation = false,
                    animatingItem = null,
                    lastPartnerCareTime = if (userId != plant.plantedByUserId) System.currentTimeMillis() else it.lastPartnerCareTime
                )
            }

            // Save to Firebase with lastCaredByUserId
            savePlantStatus(updatedPlant)
            saveInventory(updatedInventory)

            checkPlantThought()
        }
    }

    /**
     * Check and apply plant evolution
     */
    fun checkPlantEvolution() {
        val plant = _uiState.value.plant ?: return
        
        android.util.Log.d("GardenViewModel", "[GARDEN] Checking plant evolution: stage=${plant.stage}, canEvolve=${plant.canEvolve}, timeToNextStage=${plant.timeToNextStage}ms, fertilizerBoost=${plant.fertilizerBoostHours}h, isAlive=${plant.status.isAlive}")
        
        if (plant.canEvolve) {
            val nextStage = PlantStage.values().getOrNull(plant.stage.ordinal + 1)
            if (nextStage != null) {
                android.util.Log.d("GardenViewModel", "[GARDEN] 🌿 Evolving plant from ${plant.stage} to $nextStage")
                
                viewModelScope.launch {
                    // Calculate excess boost hours to carry over to next stage
                    val currentStageTimeHours = plant.stage.growthTimeHours
                    val elapsedHours = ((System.currentTimeMillis() - plant.stageStartedAt) / (1000 * 60 * 60)).toInt()
                    val totalEffectiveHours = elapsedHours + plant.fertilizerBoostHours
                    val excessBoostHours = maxOf(0, totalEffectiveHours - currentStageTimeHours)
                    
                    android.util.Log.d("GardenViewModel", "[GARDEN] 🌿 Boost calculation: elapsed=${elapsedHours}h, boost=${plant.fertilizerBoostHours}h, stageTime=${currentStageTimeHours}h, excess=${excessBoostHours}h")
                    
                    val evolvedPlant = plant.copy(
                        stage = nextStage,
                        stageStartedAt = System.currentTimeMillis(),
                        fertilizerBoostHours = excessBoostHours // Carry over excess boost
                    )

                    _uiState.update { it.copy(plant = evolvedPlant) }

                    // Save to Firebase
                    savePlantStatus(evolvedPlant)

                    if (nextStage == PlantStage.BLOOMING) {
                        unlockPlantInGallery(evolvedPlant)
                    }
                    
                    // Check if can evolve again (in case of excess boost carries over)
                    delay(500)
                    checkPlantEvolution()
                }
            }
        }
    }

    /**
     * Harvest a blooming plant and add to collection
     */
    fun harvestPlant() {
        val plant = _uiState.value.plant ?: return
        val userId = authRepository.currentUser?.uid ?: return
        
        if (!plant.isReadyToHarvest) {
            _uiState.update { it.copy(errorMessage = "Plant is not ready to harvest!") }
            return
        }

        viewModelScope.launch {
            Log.d("GardenViewModel", "[GARDEN] 🌸 Harvesting plant: ${plant.name}, type=${plant.plantType}, color=${plant.flowerColor}, rarity=${plant.rarity}")
            
            // Get user data for coupleId
            val userResult = firestoreRepository.getDocument(
                collection = "users",
                documentId = userId,
                clazz = FirebaseUser::class.java
            )
            val userData = userResult.getOrNull()
            val coupleId = userData?.coupleId ?: ""
            val partnerId = userData?.partnerId ?: ""
            
            // Check if partner contributed to care
            val partnerContributed = plant.lastCaredByUserId.isNotEmpty() && 
                                     plant.lastCaredByUserId != plant.plantedByUserId
            
            // Create collected plant
            val collectedPlant = CollectedPlant(
                id = UUID.randomUUID().toString(),
                plantType = plant.plantType,
                flowerColor = plant.flowerColor,
                rarity = plant.rarity,
                unlockedAt = System.currentTimeMillis(),
                unlockedByCoupleId = coupleId,
                harvestedByUserId = userId,
                partnerContributedCare = partnerContributed
            )
            
            // Save to collection in Firebase
            val firebaseCollectedPlant = FirebaseCollectedPlant(
                id = collectedPlant.id,
                coupleId = coupleId,
                plantType = plant.plantType.name.lowercase(),
                flowerColor = plant.flowerColor.name.lowercase(),
                rarity = plant.rarity.name.lowercase(),
                harvestedByUserId = userId,
                partnerContributedCare = partnerContributed,
                unlockedAt = Timestamp(Date())
            )
            
            firestoreRepository.setDocument(
                collection = "garden_collection",
                documentId = collectedPlant.id,
                data = firebaseCollectedPlant
            ).fold(
                onSuccess = {
                    Log.d("GardenViewModel", "[GARDEN] ✅ Plant added to collection: ${collectedPlant.id}")
                    
                    // Also unlock in gallery
                    unlockPlantInGallery(plant)
                    
                    // Delete the harvested plant
                    firestoreRepository.deleteDocument(
                        collection = "garden_plants",
                        documentId = plant.id
                    )
                    
                    // Update UI
                    val harvestResult = HarvestResult(
                        collectedPlant = collectedPlant,
                        isNewUnlock = true,
                        bonusCoins = if (partnerContributed) 50 else 25,
                        partnerBonus = partnerContributed
                    )
                    
                    _uiState.update { state ->
                        state.copy(
                            plant = null,
                            showHarvestDialog = true,
                            harvestedPlant = collectedPlant,
                            collection = state.collection + collectedPlant,
                            successMessage = if (partnerContributed) 
                                "Thu hoạch thành công! +50 xu (Bonus từ partner)" 
                            else 
                                "Thu hoạch thành công! +25 xu"
                        )
                    }
                },
                onFailure = { error ->
                    Log.e("GardenViewModel", "[GARDEN] ❌ Failed to save collection: ${error.message}")
                    _uiState.update { it.copy(errorMessage = "Không thể thu hoạch: ${error.message}") }
                }
            )
        }
    }

    /**
     * Dismiss harvest dialog
     */
    fun dismissHarvestDialog() {
        _uiState.update { it.copy(showHarvestDialog = false, harvestedPlant = null) }
    }

    /**
     * Show color editor for collected plant
     */
    fun showColorEditor(plant: CollectedPlant) {
        _uiState.update { it.copy(showColorEditor = true, harvestedPlant = plant) }
    }

    /**
     * Dismiss color editor
     */
    fun dismissColorEditor() {
        _uiState.update { it.copy(showColorEditor = false) }
    }

    /**
     * Update collected plant color customization
     */
    fun updatePlantColor(plantId: String, hue: Float, saturation: Float, brightness: Float) {
        viewModelScope.launch {
            val updates = mapOf(
                "customColorHue" to hue,
                "customSaturation" to saturation,
                "customBrightness" to brightness
            )
            
            firestoreRepository.updateDocument(
                collection = "garden_collection",
                documentId = plantId,
                updates = updates
            ).fold(
                onSuccess = {
                    _uiState.update { state ->
                        val updatedCollection = state.collection.map { plant ->
                            if (plant.id == plantId) {
                                plant.copy(
                                    customColorHue = hue,
                                    customSaturation = saturation,
                                    customBrightness = brightness
                                )
                            } else plant
                        }
                        state.copy(collection = updatedCollection, showColorEditor = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = "Không thể cập nhật màu: ${error.message}") }
                }
            )
        }
    }

    /**
     * Load collection from Firebase
     */
    private suspend fun loadCollection(coupleId: String): List<CollectedPlant> {
        val result = firestoreRepository.queryDocuments(
            collection = "garden_collection",
            field = "coupleId",
            value = coupleId,
            clazz = FirebaseCollectedPlant::class.java
        )
        
        return result.getOrNull()?.map { fb ->
            CollectedPlant(
                id = fb.id,
                plantType = PlantType.values().find { it.name.lowercase() == fb.plantType } ?: PlantType.ROSE,
                flowerColor = PlantFlowerColor.values().find { it.name.lowercase() == fb.flowerColor } ?: PlantFlowerColor.PINK,
                rarity = PlantRarity.values().find { it.name.lowercase() == fb.rarity } ?: PlantRarity.COMMON,
                customColorHue = fb.customColorHue,
                customSaturation = fb.customSaturation,
                customBrightness = fb.customBrightness,
                unlockedAt = fb.unlockedAt?.toDate()?.time ?: System.currentTimeMillis(),
                unlockedByCoupleId = fb.coupleId,
                harvestedByUserId = fb.harvestedByUserId,
                partnerContributedCare = fb.partnerContributedCare
            )
        } ?: emptyList()
    }

    /**
     * Unlock plant in gallery
     */
    private fun unlockPlantInGallery(plant: Plant) {
        val userId = authRepository.currentUser?.uid ?: return
        val galleryId = "${plant.flowerColor.name}_${plant.rarity.name}_${plant.plantType.name}"
        
        viewModelScope.launch {
            Log.d("GardenViewModel", "[GALLERY] 🔓 Unlocking plant: color=${plant.flowerColor.name}, rarity=${plant.rarity.name}, type=${plant.plantType.name}")
            
            // Get coupleId for shared gallery
            val userResult = firestoreRepository.getDocument(
                collection = "users",
                documentId = userId,
                clazz = FirebaseUser::class.java
            )
            val coupleId = userResult.getOrNull()?.coupleId ?: ""
            
            val galleryItem = FirebaseGalleryItem(
                id = UUID.randomUUID().toString(),
                userId = userId,
                coupleId = coupleId,
                flowerColor = plant.flowerColor.name.lowercase(),
                rarity = plant.rarity.name.lowercase(),
                plantType = plant.plantType.name.lowercase(),
                isUnlocked = true,
                unlockedAt = Timestamp(Date())
            )
            
            Log.d("GardenViewModel", "[GALLERY] Saving to Firestore: color=${galleryItem.flowerColor}, rarity=${galleryItem.rarity}, type=${galleryItem.plantType}")

            firestoreRepository.setDocument(
                collection = "garden_gallery",
                documentId = galleryItem.id,
                data = galleryItem
            )

            _uiState.update { state ->
                val updatedGallery = state.gallery.map {
                    if (it.id == galleryId && !it.isUnlocked) {
                        it.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis())
                    } else it
                }
                state.copy(gallery = updatedGallery)
            }
        }
    }

    /**
     * Plant a new seed
     */
    fun plantSeed(seedType: CareItemType) {
        val userId = authRepository.currentUser?.uid ?: return
        val inventory = _uiState.value.inventory
        
        if (!inventory.hasItem(seedType)) {
            _uiState.update { it.copy(errorMessage = "Bạn không có hạt giống này!") }
            return
        }

        // Determine seed rarity
        val seedRarity = when (seedType) {
            CareItemType.SEED_NORMAL -> SeedRarity.NORMAL
            CareItemType.SEED_RARE -> SeedRarity.RARE
            CareItemType.SEED_SUPER_RARE -> SeedRarity.SUPER_RARE
            else -> return
        }
        
        // Determine plant rarity from seed
        val rarity = determineRarity(seedRarity)
        
        // Random plant type - rare seeds have higher chance for rare plant types
        val plantType = determinePlantType(seedRarity)
        
        // Random flower color
        val flowerColor = determineFlowerColor(seedRarity)

        val newPlant = Plant(
            id = UUID.randomUUID().toString(),
            name = "${plantType.vietnameseName} ${flowerColor.vietnameseName}",
            stage = PlantStage.SEED,
            status = PlantStatus(),
            rarity = rarity,
            plantType = plantType,
            flowerColor = flowerColor,
            plantedByUserId = userId
        )
        
        Log.d("GardenViewModel", "[GARDEN] 🌱 Planting seed: type=${plantType.name}, color=${flowerColor.name}, rarity=${rarity.name}")

        viewModelScope.launch {
            // Get user's coupleId for shared garden
            val userResult = firestoreRepository.getDocument(
                collection = "users",
                documentId = userId,
                clazz = FirebaseUser::class.java
            )
            val coupleId = userResult.getOrNull()?.coupleId ?: ""
            Log.d("GardenViewModel", "[GARDEN] Planting seed with coupleId: $coupleId")

            // Save to Firebase
            val firebasePlant = FirebaseGardenPlant(
                id = newPlant.id,
                coupleId = coupleId,
                userId = userId,
                plantName = newPlant.name,
                stage = newPlant.stage.name.lowercase(),
                rarity = newPlant.rarity.name.lowercase(),
                plantType = newPlant.plantType.name.lowercase(),
                flowerColor = newPlant.flowerColor.name.lowercase(),
                sunlight = newPlant.status.sunlight,
                water = newPlant.status.water,
                health = newPlant.status.health,
                fertilizerBoostHours = 0,
                isInGreenhouse = newPlant.isInGreenhouse,
                plantedByUserId = userId,
                lastCaredByUserId = userId,
                stageStartedAt = System.currentTimeMillis(),
                createdAt = Date(),
                updatedAt = Date()
            )

            firestoreRepository.setDocument(
                collection = "garden_plants",
                documentId = firebasePlant.id,
                data = firebasePlant
            ).fold(
                onSuccess = {
                    val updatedInventory = inventory.useItem(seedType)
                    
                    _uiState.update {
                        it.copy(
                            plant = newPlant.copy(coupleId = coupleId),
                            inventory = updatedInventory,
                            successMessage = "Đã trồng ${newPlant.name}! ${rarity.starsCount}⭐"
                        )
                    }

                    saveInventory(updatedInventory)
                    
                    // Setup real-time listener for the new plant only if enabled
                    if (isRealTimeEnabled) {
                        setupPlantListener(firebasePlant.id)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Không thể trồng cây: ${error.message}")
                    }
                }
            )
        }
    }

    /**
     * Determine plant type based on seed rarity
     * Rare seeds unlock more exotic plant types
     */
    private fun determinePlantType(seedRarity: SeedRarity): PlantType {
        val random = Random.nextFloat()
        
        return when (seedRarity) {
            SeedRarity.NORMAL -> {
                // Common plants: Rose, Tulip, Daisy, Sunflower
                when {
                    random < 0.30f -> PlantType.ROSE
                    random < 0.55f -> PlantType.TULIP
                    random < 0.75f -> PlantType.DAISY
                    random < 0.90f -> PlantType.SUNFLOWER
                    else -> PlantType.LILY // Small chance for better plant
                }
            }
            SeedRarity.RARE -> {
                // Rare plants: Lily, Orchid, Lavender + small chance for exotic
                when {
                    random < 0.25f -> PlantType.LILY
                    random < 0.45f -> PlantType.ORCHID
                    random < 0.65f -> PlantType.LAVENDER
                    random < 0.80f -> PlantType.ROSE
                    random < 0.92f -> PlantType.CHERRY_BLOSSOM
                    else -> PlantType.LOTUS // Small chance for best plant
                }
            }
            SeedRarity.SUPER_RARE -> {
                // Super rare: Cherry Blossom, Lotus + all others possible
                when {
                    random < 0.30f -> PlantType.CHERRY_BLOSSOM
                    random < 0.55f -> PlantType.LOTUS
                    random < 0.70f -> PlantType.ORCHID
                    random < 0.85f -> PlantType.LAVENDER
                    else -> PlantType.LILY
                }
            }
        }
    }

    /**
     * Determine flower color based on seed rarity
     * Rare seeds have chance for special colors
     */
    private fun determineFlowerColor(seedRarity: SeedRarity): PlantFlowerColor {
        val random = Random.nextFloat()
        
        // Common colors
        val commonColors = listOf(
            PlantFlowerColor.PINK, PlantFlowerColor.RED, PlantFlowerColor.YELLOW,
            PlantFlowerColor.ORANGE, PlantFlowerColor.PURPLE
        )
        
        // Rare colors
        val rareColors = listOf(
            PlantFlowerColor.BLUE, PlantFlowerColor.CYAN, PlantFlowerColor.MAGENTA,
            PlantFlowerColor.GREEN
        )
        
        // Super rare colors
        val superRareColors = listOf(
            PlantFlowerColor.WHITE, PlantFlowerColor.BLACK, PlantFlowerColor.RAINBOW
        )
        
        return when (seedRarity) {
            SeedRarity.NORMAL -> {
                when {
                    random < 0.85f -> commonColors.random()
                    random < 0.97f -> rareColors.random()
                    else -> superRareColors.random()
                }
            }
            SeedRarity.RARE -> {
                when {
                    random < 0.50f -> commonColors.random()
                    random < 0.85f -> rareColors.random()
                    else -> superRareColors.random()
                }
            }
            SeedRarity.SUPER_RARE -> {
                when {
                    random < 0.20f -> commonColors.random()
                    random < 0.50f -> rareColors.random()
                    else -> superRareColors.random()
                }
            }
        }
    }

    /**
     * Determine plant rarity
     */
    private fun determineRarity(seedRarity: SeedRarity): PlantRarity {
        val random = Random.nextFloat()
        
        return when (seedRarity) {
            SeedRarity.NORMAL -> {
                when {
                    random < 0.70f -> PlantRarity.COMMON
                    random < 0.90f -> PlantRarity.UNCOMMON
                    random < 0.98f -> PlantRarity.RARE
                    else -> PlantRarity.SUPER_RARE
                }
            }
            SeedRarity.RARE -> {
                when {
                    random < 0.30f -> PlantRarity.COMMON
                    random < 0.65f -> PlantRarity.UNCOMMON
                    random < 0.90f -> PlantRarity.RARE
                    else -> PlantRarity.SUPER_RARE
                }
            }
            SeedRarity.SUPER_RARE -> {
                when {
                    random < 0.10f -> PlantRarity.UNCOMMON
                    random < 0.40f -> PlantRarity.RARE
                    else -> PlantRarity.SUPER_RARE
                }
            }
        }
    }

    /**
     * Rename plant
     */
    fun renamePlant(newName: String) {
        val plant = _uiState.value.plant ?: return
        
        viewModelScope.launch {
            val renamedPlant = plant.copy(name = newName)
            
            _uiState.update {
                it.copy(
                    plant = renamedPlant,
                    showRenameDialog = false
                )
            }

            savePlantStatus(renamedPlant)
        }
    }

    /**
     * Toggle greenhouse
     */
    fun toggleGreenhouse() {
        val plant = _uiState.value.plant ?: return
        
        viewModelScope.launch {
            val updatedPlant = plant.copy(isInGreenhouse = !plant.isInGreenhouse)
            
            _uiState.update { it.copy(plant = updatedPlant) }
            
            savePlantStatus(updatedPlant)
        }
    }

    /**
     * Save plant status to Firebase
     */
    private fun savePlantStatus(plant: Plant) {
        val userId = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            val updates = mapOf(
                "plantName" to plant.name,
                "stage" to plant.stage.name.lowercase(),
                "plantType" to plant.plantType.name.lowercase(),
                "flowerColor" to plant.flowerColor.name.lowercase(),
                "rarity" to plant.rarity.name.lowercase(),
                "sunlight" to plant.status.sunlight,
                "water" to plant.status.water,
                "health" to plant.status.health,
                "fertilizerBoostHours" to plant.fertilizerBoostHours,
                "isInGreenhouse" to plant.isInGreenhouse,
                "stageStartedAt" to plant.stageStartedAt,
                "lastCaredByUserId" to plant.lastCaredByUserId,
                "updatedAt" to Timestamp(Date())
            )

            Log.d("GardenViewModel", "[GARDEN] Saving plant status to Firebase: plantId=${plant.id}, stage=${plant.stage}, type=${plant.plantType}, boost=${plant.fertilizerBoostHours}h, lastCaredBy=${plant.lastCaredByUserId}")
            firestoreRepository.updateDocument(
                collection = "garden_plants",
                documentId = plant.id,
                updates = updates
            )
        }
    }

    /**
     * Save inventory to Firebase
     * Uses setDocument with merge to ensure document is created if it doesn't exist
     */
    private fun saveInventory(inventory: GardenInventory) {
        val userId = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            val inventoryData = FirebaseGardenInventory(
                id = userId,
                userId = userId,
                seeds = inventory.getItem(CareItemType.SEED_NORMAL)?.quantity ?: 0,
                rareSeeds = inventory.getItem(CareItemType.SEED_RARE)?.quantity ?: 0,
                superRareSeeds = inventory.getItem(CareItemType.SEED_SUPER_RARE)?.quantity ?: 0,
                fertilizer4h = inventory.getItem(CareItemType.FERTILIZER_4H)?.quantity ?: 0,
                fertilizer8h = inventory.getItem(CareItemType.FERTILIZER_8H)?.quantity ?: 0,
                fertilizer12h = inventory.getItem(CareItemType.FERTILIZER_24H)?.quantity ?: 0,
                wateringCan = inventory.getItem(CareItemType.WATER)?.quantity ?: 0,
                sunlightBottle = inventory.getItem(CareItemType.SUNLIGHT)?.quantity ?: 0,
                pesticide = inventory.getItem(CareItemType.PESTICIDE)?.quantity ?: 0,
                scissors = inventory.getItem(CareItemType.SCISSORS)?.quantity ?: 0,
                updatedAt = Date()
            )

            Log.d("GardenViewModel", "[GARDEN] Saving inventory to Firebase: seeds=${inventoryData.seeds}, water=${inventoryData.wateringCan}, sun=${inventoryData.sunlightBottle}")
            
            val result = firestoreRepository.setDocument(
                collection = "garden_inventories",
                documentId = userId,
                data = inventoryData,
                merge = true
            )
            
            result.fold(
                onSuccess = {
                    Log.d("GardenViewModel", "[GARDEN] ✓ Inventory saved successfully")
                },
                onFailure = { error ->
                    Log.e("GardenViewModel", "[GARDEN] ✗ Failed to save inventory: ${error.message}")
                }
            )
        }
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * Select tab
     */
    fun selectTab(tab: GardenTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Show/hide rename dialog
     */
    fun showRenameDialog(show: Boolean) {
        _uiState.update { it.copy(showRenameDialog = show) }
    }

    /**
     * Show/hide settings menu
     */
    fun showSettingsMenu(show: Boolean) {
        _uiState.update { it.copy(showSettingsMenu = show) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Refresh garden data
     */
    fun refreshGarden() {
        // Invalidate cache and reload
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId != null) {
                gardenCache.invalidateCache(userId)
            }
            loadGardenData(showLoading = true)
        }
    }
    
    /**
     * Cache garden data after successful Firebase load
     */
    private fun cacheGardenData(
        userId: String,
        plant: Plant?,
        inventory: FirebaseGardenInventory?,
        galleryItems: List<FirebaseGalleryItem>,
        collection: List<CollectedPlant>
    ) {
        viewModelScope.launch {
            try {
                // Cache plant
                if (plant != null) {
                    gardenCache.cachePlant(userId, plant.toCachedPlant())
                }
                
                // Cache inventory
                if (inventory != null) {
                    gardenCache.cacheInventory(userId, inventory.toCachedGardenInventory())
                }
                
                // Cache gallery
                val cachedGallery = galleryItems.map { it.toCachedGalleryItem() }
                gardenCache.cacheGallery(userId, cachedGallery)
                
                // Cache collection
                val cachedCollection = collection.map { it.toCachedCollectionPlant() }
                gardenCache.cacheCollection(userId, cachedCollection)
                
                Log.d("GardenViewModel", "💾 Garden data cached successfully")
            } catch (e: Exception) {
                Log.w("GardenViewModel", "Failed to cache garden data", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statusDecayJob?.cancel()
        thoughtBubbleJob?.cancel()
        plantListener?.remove()
        plantListener = null
    }
}

// ============ Cache Conversion Extension Functions ============

/**
 * Convert CachedPlant to domain Plant
 */
private fun CachedPlant.toPlant(): Plant {
    val stage = when (this.stage) {
        0 -> PlantStage.SEED
        1 -> PlantStage.SPROUT
        2 -> PlantStage.SEEDLING
        3 -> PlantStage.GROWING
        4 -> PlantStage.MATURE
        5 -> PlantStage.BLOOMING
        else -> PlantStage.SEED
    }
    
    val rarity = when (this.rarity.lowercase()) {
        "uncommon" -> PlantRarity.UNCOMMON
        "rare" -> PlantRarity.RARE
        "super_rare" -> PlantRarity.SUPER_RARE
        else -> PlantRarity.COMMON
    }
    
    val flowerColor = PlantFlowerColor.values().find { 
        it.name.lowercase() == this.flowerColor.lowercase() 
    } ?: PlantFlowerColor.PINK
    
    val plantType = PlantType.values().find {
        it.name.lowercase() == this.plantType.lowercase()
    } ?: PlantType.ROSE
    
    return Plant(
        id = this.id,
        name = plantType.vietnameseName,
        stage = stage,
        status = PlantStatus(
            sunlight = this.sunlight,
            water = this.water,
            health = this.health,
            lastUpdateTime = this.lastUpdated
        ),
        rarity = rarity,
        plantType = plantType,
        flowerColor = flowerColor,
        fertilizerBoostHours = this.fertilizerBoostHours,
        coupleId = this.coupleId
    )
}

/**
 * Convert domain Plant to CachedPlant
 */
private fun Plant.toCachedPlant(): CachedPlant {
    return CachedPlant(
        id = this.id,
        plantType = this.plantType.name,
        stage = this.stage.ordinal,
        water = this.status.water,
        sunlight = this.status.sunlight,
        health = this.status.health,
        flowerColor = this.flowerColor.name,
        rarity = this.rarity.name,
        fertilizerBoostHours = this.fertilizerBoostHours,
        coupleId = this.coupleId,
        lastUpdated = this.status.lastUpdateTime
    )
}

/**
 * Convert CachedGardenInventory to domain GardenInventory
 */
private fun CachedGardenInventory.toGardenInventory(): GardenInventory {
    val items = mapOf(
        CareItemType.WATER to createDefaultItem(CareItemType.WATER).copy(quantity = this.waterAmount),
        CareItemType.SUNLIGHT to createDefaultItem(CareItemType.SUNLIGHT).copy(quantity = this.sunlightAmount),
        CareItemType.FERTILIZER_4H to createDefaultItem(CareItemType.FERTILIZER_4H).copy(quantity = this.fertilizerAmount)
    )
    return GardenInventory(items = items)
}

/**
 * Convert FirebaseGardenInventory to CachedGardenInventory
 */
private fun FirebaseGardenInventory.toCachedGardenInventory(): CachedGardenInventory {
    return CachedGardenInventory(
        waterAmount = this.wateringCan,
        sunlightAmount = this.sunlightBottle,
        fertilizerAmount = this.fertilizer4h + this.fertilizer8h + this.fertilizer12h,
        seeds = listOf()
    )
}

/**
 * Convert CachedGalleryItem to domain GalleryPlant
 */
private fun CachedGalleryItem.toGalleryPlant(): GalleryPlant {
    val flowerColor = PlantFlowerColor.values().find { 
        it.name.lowercase() == this.flowerColor.lowercase() 
    } ?: PlantFlowerColor.PINK
    
    val rarity = when (this.rarity.lowercase()) {
        "uncommon" -> PlantRarity.UNCOMMON
        "rare" -> PlantRarity.RARE
        "super_rare" -> PlantRarity.SUPER_RARE
        else -> PlantRarity.COMMON
    }
    
    val plantType = PlantType.values().find {
        it.name.lowercase() == this.plantType.lowercase()
    } ?: PlantType.ROSE
    
    return GalleryPlant(
        id = this.id,
        flowerColor = flowerColor,
        rarity = rarity,
        plantType = plantType,
        isUnlocked = this.isUnlocked,
        unlockedAt = this.unlockedDate
    )
}

/**
 * Convert FirebaseGalleryItem to CachedGalleryItem
 */
private fun FirebaseGalleryItem.toCachedGalleryItem(): CachedGalleryItem {
    return CachedGalleryItem(
        id = this.id,
        flowerColor = this.flowerColor,
        rarity = this.rarity,
        plantType = this.plantType,
        isUnlocked = this.isUnlocked,
        unlockedDate = this.unlockedDate?.time
    )
}

/**
 * Convert CachedCollectionPlant to domain CollectedPlant
 */
private fun CachedCollectionPlant.toCollectedPlant(): CollectedPlant {
    val plantType = PlantType.values().find {
        it.name.lowercase() == this.plantType.lowercase()
    } ?: PlantType.ROSE
    
    val flowerColor = PlantFlowerColor.values().find { 
        it.name.lowercase() == this.flowerColor.lowercase() 
    } ?: PlantFlowerColor.PINK
    
    val rarity = when (this.rarity.lowercase()) {
        "uncommon" -> PlantRarity.UNCOMMON
        "rare" -> PlantRarity.RARE
        "super_rare" -> PlantRarity.SUPER_RARE
        else -> PlantRarity.COMMON
    }
    
    return CollectedPlant(
        id = this.id,
        plantType = plantType,
        flowerColor = flowerColor,
        rarity = rarity,
        unlockedAt = this.harvestedAt
    )
}

/**
 * Convert domain CollectedPlant to CachedCollectionPlant
 */
private fun CollectedPlant.toCachedCollectionPlant(): CachedCollectionPlant {
    return CachedCollectionPlant(
        id = this.id,
        plantType = this.plantType.name,
        flowerColor = this.flowerColor.name,
        rarity = this.rarity.name,
        harvestedAt = this.unlockedAt
    )
}