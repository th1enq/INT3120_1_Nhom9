package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.google.firebase.Timestamp
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
 */
class GardenViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    private val _uiState = MutableStateFlow(GardenUiState())
    val uiState: StateFlow<GardenUiState> = _uiState.asStateFlow()

    private var statusDecayJob: Job? = null
    private var thoughtBubbleJob: Job? = null

    init {
        loadGardenData()
        startStatusDecay()
        startThoughtBubbleCheck()
    }

    /**
     * Load garden data from Firebase
     */
    private fun loadGardenData() {
        _uiState.update { it.copy(isLoading = true) }

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
                // Load plant
                val plantResult = firestoreRepository.queryDocuments(
                    collection = "garden_plants",
                    field = "userId",
                    value = userId,
                    clazz = FirebaseGardenPlant::class.java
                )

                // Load inventory from Store purchases
                val inventoryResult = firestoreRepository.getDocument(
                    collection = "garden_inventories",
                    documentId = userId,
                    clazz = FirebaseGardenInventory::class.java
                )

                // Load gallery
                val galleryResult = firestoreRepository.queryDocuments(
                    collection = "garden_gallery",
                    field = "userId",
                    value = userId,
                    clazz = FirebaseGalleryItem::class.java
                )

                plantResult.fold(
                    onSuccess = { plants ->
                        val plant = plants.firstOrNull()?.toPlant()
                        
                        inventoryResult.fold(
                            onSuccess = { firebaseInventory ->
                                val inventory = firebaseInventory?.toGardenInventory() 
                                    ?: createDefaultInventory()
                                
                                galleryResult.fold(
                                    onSuccess = { galleryItems ->
                                        val gallery = createGalleryWithUnlocks(galleryItems)
                                        
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                plant = plant,
                                                inventory = inventory,
                                                gallery = gallery
                                            )
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

        return Plant(
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
            flowerColor = flowerColor,
            isInGreenhouse = this.isInGreenhouse,
            stageStartedAt = this.createdAt?.time ?: System.currentTimeMillis()
        )
    }

    /**
     * Convert Firebase inventory to domain model
     */
    private fun FirebaseGardenInventory.toGardenInventory(): GardenInventory {
        val items = mutableMapOf<CareItemType, CareItem>()

        if (this.seeds > 0) {
            items[CareItemType.SEED_NORMAL] = createDefaultItem(CareItemType.SEED_NORMAL)
                .copy(quantity = this.seeds)
        }
        if (this.fertilizer4h > 0) {
            items[CareItemType.FERTILIZER_4H] = createDefaultItem(CareItemType.FERTILIZER_4H)
                .copy(quantity = this.fertilizer4h)
        }
        if (this.fertilizer8h > 0) {
            items[CareItemType.FERTILIZER_8H] = createDefaultItem(CareItemType.FERTILIZER_8H)
                .copy(quantity = this.fertilizer8h)
        }
        if (this.fertilizer12h > 0) {
            items[CareItemType.FERTILIZER_24H] = createDefaultItem(CareItemType.FERTILIZER_24H)
                .copy(quantity = this.fertilizer12h)
        }
        if (this.wateringCan > 0) {
            items[CareItemType.WATER] = createDefaultItem(CareItemType.WATER)
                .copy(quantity = this.wateringCan)
        }
        if (this.sunlightBottle > 0) {
            items[CareItemType.SUNLIGHT] = createDefaultItem(CareItemType.SUNLIGHT)
                .copy(quantity = this.sunlightBottle)
        }

        return GardenInventory(
            items = items,
            lastUpdated = this.updatedAt?.time ?: System.currentTimeMillis()
        )
    }

    /**
     * Create gallery with unlocked items
     */
    private fun createGalleryWithUnlocks(galleryItems: List<FirebaseGalleryItem>): List<GalleryPlant> {
        val unlockedSet = galleryItems.map { "${it.flowerColor}_${it.rarity}" }.toSet()
        
        return PlantFlowerColor.values().flatMap { color ->
            PlantRarity.values().map { rarity ->
                val id = "${color.name}_${rarity.name}"
                val isUnlocked = id in unlockedSet
                val unlockedItem = galleryItems.find { "${it.flowerColor}_${it.rarity}" == id }
                
                GalleryPlant(
                    id = id,
                    flowerColor = color,
                    rarity = rarity,
                    isUnlocked = isUnlocked,
                    unlockedAt = unlockedItem?.unlockedAt?.toDate()?.time
                )
            }
        }
    }

    /**
     * Create default gallery
     */
    private fun createDefaultGallery(): List<GalleryPlant> {
        return PlantFlowerColor.values().flatMap { color ->
            PlantRarity.values().map { rarity ->
                GalleryPlant(
                    id = "${color.name}_${rarity.name}",
                    flowerColor = color,
                    rarity = rarity,
                    isUnlocked = false
                )
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
            }
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
        val decayAmount = (timeSinceUpdate / 3600000f) * 5f * decayRate

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

            // Apply effect
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
                CareItemType.FERTILIZER_4H, CareItemType.FERTILIZER_8H, CareItemType.FERTILIZER_24H -> {
                    applyFertilizer(plant, item.boostHours)
                    plant.status
                }
                else -> plant.status
            }

            val updatedPlant = plant.copy(status = newStatus)
            val updatedInventory = inventory.useItem(itemType)

            // Update UI
            _uiState.update {
                it.copy(
                    plant = updatedPlant,
                    inventory = updatedInventory,
                    showItemAnimation = false,
                    animatingItem = null
                )
            }

            // Save to Firebase
            savePlantStatus(updatedPlant)
            saveInventory(updatedInventory)

            checkPlantThought()
        }
    }

    /**
     * Apply fertilizer boost
     */
    private fun applyFertilizer(plant: Plant, boostHours: Int) {
        val boostMillis = boostHours * 60 * 60 * 1000L
        val newStageStartedAt = plant.stageStartedAt - boostMillis

        _uiState.update {
            it.copy(
                plant = plant.copy(stageStartedAt = newStageStartedAt)
            )
        }

        checkPlantEvolution()
    }

    /**
     * Check and apply plant evolution
     */
    fun checkPlantEvolution() {
        val plant = _uiState.value.plant ?: return
        
        if (plant.canEvolve) {
            val nextStage = PlantStage.values().getOrNull(plant.stage.ordinal + 1)
            if (nextStage != null) {
                viewModelScope.launch {
                    val evolvedPlant = plant.copy(
                        stage = nextStage,
                        stageStartedAt = System.currentTimeMillis()
                    )

                    _uiState.update { it.copy(plant = evolvedPlant) }

                    // Save to Firebase
                    savePlantStatus(evolvedPlant)

                    if (nextStage == PlantStage.BLOOMING) {
                        unlockPlantInGallery(evolvedPlant)
                    }
                }
            }
        }
    }

    /**
     * Unlock plant in gallery
     */
    private fun unlockPlantInGallery(plant: Plant) {
        val userId = authRepository.currentUser?.uid ?: return
        val galleryId = "${plant.flowerColor.name}_${plant.rarity.name}"
        
        viewModelScope.launch {
            val galleryItem = FirebaseGalleryItem(
                id = UUID.randomUUID().toString(),
                userId = userId,
                coupleId = "",
                flowerColor = plant.flowerColor.name,
                rarity = plant.rarity.name,
                unlockedAt = Timestamp(Date())
            )

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

        val rarity = when (seedType) {
            CareItemType.SEED_NORMAL -> determineRarity(SeedRarity.NORMAL)
            CareItemType.SEED_RARE -> determineRarity(SeedRarity.RARE)
            CareItemType.SEED_SUPER_RARE -> determineRarity(SeedRarity.SUPER_RARE)
            else -> return
        }

        val flowerColor = PlantFlowerColor.values().random()

        val newPlant = Plant(
            id = UUID.randomUUID().toString(),
            name = "Cây trồng mới",
            stage = PlantStage.SEED,
            status = PlantStatus(),
            rarity = rarity,
            flowerColor = flowerColor
        )

        viewModelScope.launch {
            // Save to Firebase
            val firebasePlant = FirebaseGardenPlant(
                id = newPlant.id,
                coupleId = "",
                userId = userId,
                plantName = newPlant.name,
                stage = newPlant.stage.name.lowercase(),
                rarity = newPlant.rarity.name.lowercase(),
                flowerColor = newPlant.flowerColor.name.lowercase(),
                sunlight = newPlant.status.sunlight,
                water = newPlant.status.water,
                health = newPlant.status.health,
                isInGreenhouse = newPlant.isInGreenhouse,
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
                            plant = newPlant,
                            inventory = updatedInventory
                        )
                    }

                    saveInventory(updatedInventory)
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
                "sunlight" to plant.status.sunlight,
                "water" to plant.status.water,
                "health" to plant.status.health,
                "isInGreenhouse" to plant.isInGreenhouse,
                "updatedAt" to Timestamp(Date())
            )

            firestoreRepository.updateDocument(
                collection = "garden_plants",
                documentId = plant.id,
                updates = updates
            )
        }
    }

    /**
     * Save inventory to Firebase
     */
    private fun saveInventory(inventory: GardenInventory) {
        val userId = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            val updates = mapOf(
                "seeds" to (inventory.getItem(CareItemType.SEED_NORMAL)?.quantity ?: 0),
                "fertilizer4h" to (inventory.getItem(CareItemType.FERTILIZER_4H)?.quantity ?: 0),
                "fertilizer8h" to (inventory.getItem(CareItemType.FERTILIZER_8H)?.quantity ?: 0),
                "fertilizer12h" to (inventory.getItem(CareItemType.FERTILIZER_24H)?.quantity ?: 0),
                "wateringCan" to (inventory.getItem(CareItemType.WATER)?.quantity ?: 0),
                "sunlightBottle" to (inventory.getItem(CareItemType.SUNLIGHT)?.quantity ?: 0),
                "updatedAt" to Timestamp(Date())
            )

            firestoreRepository.updateDocument(
                collection = "garden_inventories",
                documentId = userId,
                updates = updates
            )
        }
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
        loadGardenData()
    }

    override fun onCleared() {
        super.onCleared()
        statusDecayJob?.cancel()
        thoughtBubbleJob?.cancel()
    }
}
