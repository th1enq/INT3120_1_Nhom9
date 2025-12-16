package com.example.coupleapp.data.model

import androidx.annotation.DrawableRes
import com.example.coupleapp.R

/**
 * Plant growth stages - 6 stages from seed to blooming
 */
enum class PlantStage(
    val displayName: String,
    val vietnameseName: String,
    @DrawableRes val imageRes: Int,
    val growthTimeHours: Int // Time to reach next stage
) {
    SEED("Seed", "Hạt giống", R.drawable.seed, 24),
    SPROUT("Sprout", "Mầm", R.drawable.sprout, 30),
    SEEDLING("Seedling", "Cây con", R.drawable.seedling, 36),
    GROWING("Growing", "Đang lớn", R.drawable.growing, 48),
    MATURE("Mature", "Trưởng thành", R.drawable.mature, 72),
    BLOOMING("Blooming", "Nở hoa", R.drawable.blooming, 0) // Final stage
}

/**
 * Plant rarity levels
 */
enum class PlantRarity(
    val displayName: String,
    val vietnameseName: String,
    val colorHue: Float, // For bloom color variation
    val dropRate: Float
) {
    COMMON("Common", "Thường", 0f, 0.6f),
    UNCOMMON("Uncommon", "Không phổ biến", 30f, 0.25f),
    RARE("Rare", "Hiếm", 180f, 0.12f),
    SUPER_RARE("Super Rare", "Siêu hiếm", 280f, 0.03f)
}

/**
 * Plant flower colors for gallery collection
 */
enum class PlantFlowerColor(
    val displayName: String,
    val colorHue: Float,
    @DrawableRes val bloomingRes: Int
) {
    PINK("Pink", 330f, R.drawable.blooming),
    RED("Red", 0f, R.drawable.blooming),
    ORANGE("Orange", 30f, R.drawable.blooming),
    YELLOW("Yellow", 60f, R.drawable.blooming),
    GREEN("Green", 120f, R.drawable.blooming),
    CYAN("Cyan", 180f, R.drawable.blooming),
    BLUE("Blue", 210f, R.drawable.blooming),
    PURPLE("Purple", 270f, R.drawable.blooming),
    MAGENTA("Magenta", 300f, R.drawable.blooming)
}

/**
 * Status bar types for plant care
 */
enum class PlantStatusType(
    val displayName: String,
    val vietnameseName: String,
    @DrawableRes val iconRes: Int
) {
    SUNLIGHT("Sunlight", "Sunlight", R.drawable.sun),
    WATER("Water", "Water", R.drawable.xoa),
    HEALTH("Health", "Health", R.drawable.xit) // Represents pest/weed status
}

/**
 * Care item types
 */
enum class CareItemType {
    WATER,           // Watering can
    SUNLIGHT,        // Sun lamp
    PESTICIDE,       // Bug spray
    SCISSORS,        // Pruning scissors
    FERTILIZER_4H,   // 4 hour boost
    FERTILIZER_8H,   // 8 hour boost
    FERTILIZER_24H,  // 24 hour boost
    SEED_NORMAL,     // Normal seed
    SEED_RARE,       // Rare seed
    SEED_SUPER_RARE  // Super rare seed
}

/**
 * Care item for inventory
 */
data class CareItem(
    val id: String,
    val type: CareItemType,
    val name: String,
    val vietnameseName: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val quantity: Int = 0,
    val effectValue: Float = 25f, // How much it affects the status bar
    val boostHours: Int = 0 // For fertilizers
)

/**
 * Plant status values
 */
data class PlantStatus(
    val sunlight: Float = 100f,   // 0-100
    val water: Float = 100f,      // 0-100
    val health: Float = 100f,     // 0-100
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    val isAlive: Boolean
        get() = sunlight > 0 || water > 0 || health > 0
    
    val isDead: Boolean
        get() = sunlight <= 0 && water <= 0 && health <= 0
    
    val needsSunlight: Boolean
        get() = sunlight < 30
    
    val needsWater: Boolean
        get() = water < 30
    
    val needsHealth: Boolean
        get() = health < 30
    
    val mostNeededStatus: PlantStatusType?
        get() = when {
            sunlight <= 0 && water <= 0 && health <= 0 -> null
            sunlight <= water && sunlight <= health -> PlantStatusType.SUNLIGHT
            water <= sunlight && water <= health -> PlantStatusType.WATER
            else -> PlantStatusType.HEALTH
        }
}

/**
 * Main Plant data class
 */
data class Plant(
    val id: String,
    val name: String,
    val stage: PlantStage = PlantStage.SEED,
    val status: PlantStatus = PlantStatus(),
    val rarity: PlantRarity = PlantRarity.COMMON,
    val flowerColor: PlantFlowerColor = PlantFlowerColor.PINK,
    val plantedAt: Long = System.currentTimeMillis(),
    val stageStartedAt: Long = System.currentTimeMillis(),
    val growthProgress: Float = 0f, // 0-1 progress to next stage
    val isInGreenhouse: Boolean = false,
    val greenhouseBoost: Float = 1.5f, // 150% growth speed
    val coupleId: String = "", // For sync between couple
    val lastSyncTime: Long = System.currentTimeMillis()
) {
    val timeToNextStage: Long
        get() {
            if (stage == PlantStage.BLOOMING) return 0
            val baseTime = stage.growthTimeHours * 60 * 60 * 1000L // Convert to milliseconds
            val adjustedTime = if (isInGreenhouse) (baseTime / greenhouseBoost).toLong() else baseTime
            val elapsed = System.currentTimeMillis() - stageStartedAt
            return maxOf(0, adjustedTime - elapsed)
        }
    
    val canEvolve: Boolean
        get() = stage != PlantStage.BLOOMING && timeToNextStage <= 0 && status.isAlive
}

/**
 * Gallery collection item
 */
data class GalleryPlant(
    val id: String,
    val flowerColor: PlantFlowerColor,
    val rarity: PlantRarity,
    val unlockedAt: Long? = null,
    val isUnlocked: Boolean = false
)

/**
 * User's garden inventory
 */
data class GardenInventory(
    val items: Map<CareItemType, CareItem> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getItem(type: CareItemType): CareItem? = items[type]
    
    fun hasItem(type: CareItemType): Boolean = (items[type]?.quantity ?: 0) > 0
    
    fun useItem(type: CareItemType): GardenInventory {
        val item = items[type] ?: return this
        if (item.quantity <= 0) return this
        return copy(
            items = items + (type to item.copy(quantity = item.quantity - 1)),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    fun addItem(type: CareItemType, quantity: Int = 1): GardenInventory {
        val item = items[type] ?: createDefaultItem(type)
        return copy(
            items = items + (type to item.copy(quantity = item.quantity + quantity)),
            lastUpdated = System.currentTimeMillis()
        )
    }
}

/**
 * Create default care items
 */
fun createDefaultItem(type: CareItemType): CareItem {
    return when (type) {
        CareItemType.WATER -> CareItem(
            id = "water",
            type = type,
            name = "Water",
            vietnameseName = "Water",
            description = "Water the plant",
            iconRes = R.drawable.xoa,
            effectValue = 30f
        )
        CareItemType.SUNLIGHT -> CareItem(
            id = "sunlight",
            type = type,
            name = "Sunlight",
            vietnameseName = "Sunlight",
            description = "Provide sunlight for the plant",
            iconRes = R.drawable.sun,
            effectValue = 30f
        )
        CareItemType.PESTICIDE -> CareItem(
            id = "pesticide",
            type = type,
            name = "Pesticide",
            vietnameseName = "Pesticide",
            description = "Protect plant from pests",
            iconRes = R.drawable.xit,
            effectValue = 35f
        )
        CareItemType.SCISSORS -> CareItem(
            id = "scissors",
            type = type,
            name = "Scissors",
            vietnameseName = "Scissors",
            description = "Trim branches and leaves",
            iconRes = R.drawable.keo,
            effectValue = 20f
        )
        CareItemType.FERTILIZER_4H -> CareItem(
            id = "fertilizer_4h",
            type = type,
            name = "4h Fertilizer",
            vietnameseName = "4h Boost",
            description = "Speed up 4 hours",
            iconRes = R.drawable.phan4h,
            boostHours = 4
        )
        CareItemType.FERTILIZER_8H -> CareItem(
            id = "fertilizer_8h",
            type = type,
            name = "8h Fertilizer",
            vietnameseName = "8h Boost",
            description = "Speed up 8 hours",
            iconRes = R.drawable.phan8h,
            boostHours = 8
        )
        CareItemType.FERTILIZER_24H -> CareItem(
            id = "fertilizer_24h",
            type = type,
            name = "24h Fertilizer",
            vietnameseName = "24h Boost",
            description = "Speed up 24 hours",
            iconRes = R.drawable.phan24h,
            boostHours = 24
        )
        CareItemType.SEED_NORMAL -> CareItem(
            id = "seed_normal",
            type = type,
            name = "Normal Seed",
            vietnameseName = "Normal Seed",
            description = "Basic seed",
            iconRes = R.drawable.normal_seed
        )
        CareItemType.SEED_RARE -> CareItem(
            id = "seed_rare",
            type = type,
            name = "Rare Seed",
            vietnameseName = "Rare Seed",
            description = "Rare seed",
            iconRes = R.drawable.rare_seed
        )
        CareItemType.SEED_SUPER_RARE -> CareItem(
            id = "seed_super_rare",
            type = type,
            name = "Super Rare Seed",
            vietnameseName = "Super Rare",
            description = "Super rare seed",
            iconRes = R.drawable.super_rare_seed
        )
    }
}

/**
 * Default inventory with initial items
 */
fun createDefaultInventory(): GardenInventory {
    return GardenInventory(
        items = mapOf(
            CareItemType.WATER to createDefaultItem(CareItemType.WATER).copy(quantity = 5),
            CareItemType.SUNLIGHT to createDefaultItem(CareItemType.SUNLIGHT).copy(quantity = 3),
            CareItemType.PESTICIDE to createDefaultItem(CareItemType.PESTICIDE).copy(quantity = 2),
            CareItemType.SCISSORS to createDefaultItem(CareItemType.SCISSORS).copy(quantity = 2),
            CareItemType.FERTILIZER_4H to createDefaultItem(CareItemType.FERTILIZER_4H).copy(quantity = 1),
            CareItemType.FERTILIZER_8H to createDefaultItem(CareItemType.FERTILIZER_8H).copy(quantity = 1),
            CareItemType.FERTILIZER_24H to createDefaultItem(CareItemType.FERTILIZER_24H).copy(quantity = 0),
            CareItemType.SEED_NORMAL to createDefaultItem(CareItemType.SEED_NORMAL).copy(quantity = 3),
            CareItemType.SEED_RARE to createDefaultItem(CareItemType.SEED_RARE).copy(quantity = 1),
            CareItemType.SEED_SUPER_RARE to createDefaultItem(CareItemType.SEED_SUPER_RARE).copy(quantity = 0)
        )
    )
}

/**
 * Garden UI State
 */
data class GardenUiState(
    val isLoading: Boolean = true,
    val plant: Plant? = null,
    val inventory: GardenInventory = createDefaultInventory(),
    val gallery: List<GalleryPlant> = emptyList(),
    val selectedTab: GardenTab = GardenTab.CARE,
    val showRenameDialog: Boolean = false,
    val showSettingsMenu: Boolean = false,
    val showItemAnimation: Boolean = false,
    val animatingItem: CareItem? = null,
    val plantThought: PlantStatusType? = null, // For thought bubble
    val errorMessage: String? = null,
    val coupleId: String = "",
    val partnerId: String = "",
    val isConnected: Boolean = true
)

/**
 * Garden tabs
 */
enum class GardenTab(val displayName: String, val vietnameseName: String) {
    CARE("Care", "Care"),
    BOOST("Boost", "Boost"),
    SEEDS("Seeds", "Seeds")
}

/**
 * Item use animation state
 */
data class ItemAnimationState(
    val item: CareItem,
    val isAnimating: Boolean = false,
    val progress: Float = 0f, // 0-1 animation progress
    val startPosition: Pair<Float, Float> = 0f to 0f,
    val targetPosition: Pair<Float, Float> = 0f to 0f
)
