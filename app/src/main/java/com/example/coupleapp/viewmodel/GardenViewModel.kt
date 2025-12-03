package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * ViewModel for Garden Screen
 * Manages plant state, inventory, and animations
 */
class GardenViewModel : ViewModel() {

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
     * Load garden data from repository
     */
    private fun loadGardenData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                delay(800) // Simulate loading

                // TODO: Load from Firebase/local storage
                val existingPlant = loadPlant()
                val inventory = loadInventory()
                val gallery = loadGallery()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        plant = existingPlant,
                        inventory = inventory,
                        gallery = gallery
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể tải dữ liệu vườn"
                    )
                }
            }
        }
    }

    /**
     * Load plant from storage
     */
    private suspend fun loadPlant(): Plant? {
        // TODO: Load from Firebase
        // Return a demo plant for now
        return Plant(
            id = UUID.randomUUID().toString(),
            name = "Cây trồng",
            stage = PlantStage.SPROUT,
            status = PlantStatus(
                sunlight = 75f,
                water = 60f,
                health = 80f
            ),
            rarity = PlantRarity.COMMON,
            flowerColor = PlantFlowerColor.PINK
        )
    }

    /**
     * Load inventory from storage
     */
    private suspend fun loadInventory(): GardenInventory {
        // TODO: Load from Firebase
        return createDefaultInventory()
    }

    /**
     * Load gallery from storage
     */
    private suspend fun loadGallery(): List<GalleryPlant> {
        // Create gallery with all possible plants
        return PlantFlowerColor.values().flatMap { color ->
            PlantRarity.values().map { rarity ->
                GalleryPlant(
                    id = "${color.name}_${rarity.name}",
                    flowerColor = color,
                    rarity = rarity,
                    isUnlocked = false // TODO: Load unlock status from storage
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

        val decayRate = if (plant.isInGreenhouse) 0.5f else 1f // Slower decay in greenhouse
        val timeSinceUpdate = System.currentTimeMillis() - plant.status.lastUpdateTime
        val decayAmount = (timeSinceUpdate / 3600000f) * 5f * decayRate // 5 points per hour

        val newStatus = plant.status.copy(
            sunlight = (plant.status.sunlight - decayAmount).coerceIn(0f, 100f),
            water = (plant.status.water - decayAmount * 1.2f).coerceIn(0f, 100f), // Water decays faster
            health = (plant.status.health - decayAmount * 0.8f).coerceIn(0f, 100f),
            lastUpdateTime = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(plant = plant.copy(status = newStatus))
        }

        // Check if plant died
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
                delay(5000) // Check every 5 seconds
                checkPlantThought()
            }
        }
    }

    /**
     * Check if plant needs something and show thought bubble
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
            // TODO: Save dead plant record
            // TODO: Notify partner
            _uiState.update {
                it.copy(errorMessage = "Cây của bạn đã chết! Hãy trồng cây mới.")
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

            // Wait for animation
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
                    // Reduce time to next stage
                    applyFertilizer(plant, item.boostHours)
                    plant.status
                }
                else -> plant.status
            }

            // Update state
            _uiState.update {
                it.copy(
                    plant = plant.copy(status = newStatus),
                    inventory = inventory.useItem(itemType),
                    showItemAnimation = false,
                    animatingItem = null
                )
            }

            // Check thought bubble
            checkPlantThought()

            // TODO: Sync with partner
            syncWithPartner()
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

        // Check if plant can evolve
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
                    _uiState.update {
                        it.copy(
                            plant = plant.copy(
                                stage = nextStage,
                                stageStartedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    // If blooming, unlock in gallery
                    if (nextStage == PlantStage.BLOOMING) {
                        unlockPlantInGallery(plant)
                    }

                    syncWithPartner()
                }
            }
        }
    }

    /**
     * Unlock plant in gallery collection
     */
    private fun unlockPlantInGallery(plant: Plant) {
        val galleryId = "${plant.flowerColor.name}_${plant.rarity.name}"
        
        _uiState.update { state ->
            val updatedGallery = state.gallery.map {
                if (it.id == galleryId && !it.isUnlocked) {
                    it.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis())
                } else it
            }
            state.copy(gallery = updatedGallery)
        }
    }

    /**
     * Plant a new seed
     */
    fun plantSeed(seedType: CareItemType) {
        val inventory = _uiState.value.inventory
        
        if (!inventory.hasItem(seedType)) {
            _uiState.update { it.copy(errorMessage = "Bạn không có hạt giống này!") }
            return
        }

        // Determine rarity based on seed type
        val rarity = when (seedType) {
            CareItemType.SEED_NORMAL -> determineRarity(SeedRarity.NORMAL)
            CareItemType.SEED_RARE -> determineRarity(SeedRarity.RARE)
            CareItemType.SEED_SUPER_RARE -> determineRarity(SeedRarity.SUPER_RARE)
            else -> return
        }

        // Random flower color
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
            _uiState.update {
                it.copy(
                    plant = newPlant,
                    inventory = inventory.useItem(seedType)
                )
            }

            syncWithPartner()
        }
    }

    /**
     * Determine plant rarity based on seed type
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
            _uiState.update {
                it.copy(
                    plant = plant.copy(name = newName),
                    showRenameDialog = false
                )
            }

            syncWithPartner()
        }
    }

    /**
     * Toggle greenhouse
     */
    fun toggleGreenhouse() {
        val plant = _uiState.value.plant ?: return
        
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    plant = plant.copy(isInGreenhouse = !plant.isInGreenhouse)
                )
            }

            syncWithPartner()
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
     * Sync with partner (Firebase)
     */
    private fun syncWithPartner() {
        viewModelScope.launch {
            // TODO: Implement Firebase sync
            val state = _uiState.value
            // Upload plant state, inventory, and gallery to Firebase
            // Listen for partner's changes
        }
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
