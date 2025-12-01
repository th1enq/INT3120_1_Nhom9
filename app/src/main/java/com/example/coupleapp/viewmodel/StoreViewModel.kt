package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Store Screen
 * Manages store items, purchases, and user wallet
 */
class StoreViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        loadStoreData()
    }

    /**
     * Load store data including categories and items
     */
    private fun loadStoreData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                delay(800) // Simulate loading

                val categories = createStoreCategories()
                val wallet = loadUserWallet()
                val canClaimFree = checkFreeGiftAvailability()
                val cooldownDays = calculateCooldownDays()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        userWallet = wallet,
                        canClaimFreeGift = canClaimFree,
                        freeGiftCooldownDays = cooldownDays
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to load store data"
                    )
                }
            }
        }
    }

    /**
     * Create store categories with items
     */
    private fun createStoreCategories(): List<StoreCategory> {
        return listOf(

            // Combo Packages
            StoreCategory(
                id = "combos",
                name = "Plant Care Packages",
                items = listOf(
                    StoreItem(
                        id = "combo_free",
                        name = "Free Package",
                        description = "Claim free every 3 days",
                        iconRes = R.drawable.combo1,
                        type = StoreItemType.CARE_PACKAGE,
                        purchaseType = PurchaseType.FREE_DAILY,
                        cooldownDays = 3
                    ),
                    StoreItem(
                        id = "combo_ad",
                        name = "Ad Package",
                        description = "Watch ad to claim",
                        iconRes = R.drawable.combo2,
                        type = StoreItemType.CARE_PACKAGE,
                        purchaseType = PurchaseType.WATCH_AD
                    ),
                    StoreItem(
                        id = "combo_premium",
                        name = "Premium Package",
                        description = "Premium plant care bundle",
                        iconRes = R.drawable.combo3,
                        type = StoreItemType.CARE_PACKAGE,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 2000
                    )
                )
            ),

            // Plant Seeds
            StoreCategory(
                id = "seeds",
                name = "Plant Seeds",
                items = listOf(
                    StoreItem(
                        id = "seed_normal",
                        name = "Normal Seed",
                        description = "Basic plant seed",
                        iconRes = R.drawable.normal_seed,
                        type = StoreItemType.SEED,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        rarity = SeedRarity.NORMAL
                    ),
                    StoreItem(
                        id = "seed_rare",
                        name = "Rare Seed",
                        description = "Rare plant seed",
                        iconRes = R.drawable.rare_seed,
                        type = StoreItemType.SEED,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        rarity = SeedRarity.RARE
                    ),
                    StoreItem(
                        id = "seed_super_rare",
                        name = "Super Rare Seed",
                        description = "Special plant seed",
                        iconRes = R.drawable.super_rare_seed,
                        type = StoreItemType.SEED,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        rarity = SeedRarity.SUPER_RARE
                    )
                )
            ),

            // Fertilizers
            StoreCategory(
                id = "fertilizers",
                name = "Fertilizers",
                items = listOf(
                    StoreItem(
                        id = "fertilizer_4h",
                        name = "4h Fertilizer",
                        description = "Speed up growth by 4 hours",
                        iconRes = R.drawable.phan4h,
                        type = StoreItemType.FERTILIZER,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        durationHours = 4
                    ),
                    StoreItem(
                        id = "fertilizer_8h",
                        name = "8h Fertilizer",
                        description = "Speed up growth by 8 hours",
                        iconRes = R.drawable.phan8h,
                        type = StoreItemType.FERTILIZER,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        durationHours = 8
                    ),
                    StoreItem(
                        id = "fertilizer_24h",
                        name = "24h Fertilizer",
                        description = "Speed up growth by 24 hours",
                        iconRes = R.drawable.phan24h,
                        type = StoreItemType.FERTILIZER,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100,
                        durationHours = 24
                    )
                )
            ),

            // Gardening Tools
            StoreCategory(
                id = "tools",
                name = "Gardening Tools",
                items = listOf(
                    StoreItem(
                        id = "tool_sun",
                        name = "Sun Lamp",
                        description = "Provide light for plants",
                        iconRes = R.drawable.sun,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 150
                    ),
                    StoreItem(
                        id = "tool_keo",
                        name = "Watering Can",
                        description = "Water your plants",
                        iconRes = R.drawable.keo,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 150
                    ),
                    StoreItem(
                        id = "tool_xit",
                        name = "Pesticide",
                        description = "Protect plants from pests",
                        iconRes = R.drawable.xit,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 150
                    ),
                    StoreItem(
                        id = "tool_xoa",
                        name = "Plant Scrubber",
                        description = "Clean your plants",
                        iconRes = R.drawable.xoa,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 150
                    )
                )
            )
        )
    }

    /**
     * Load user wallet from repository
     */
    private suspend fun loadUserWallet(): UserWallet {
        // TODO: Load from Firebase/local storage
        return UserWallet(
            coins = 500,
            lastFreeClaimTime = null,
            lastAdWatchTime = null,
            adsWatchedToday = 0
        )
    }

    /**
     * Check if free gift is available
     */
    private fun checkFreeGiftAvailability(): Boolean {
        val lastClaimTime = _uiState.value.userWallet.lastFreeClaimTime
        if (lastClaimTime == null) return true

        val daysSinceLastClaim = (System.currentTimeMillis() - lastClaimTime) / (1000 * 60 * 60 * 24)
        return daysSinceLastClaim >= 3
    }

    /**
     * Calculate cooldown days remaining
     */
    private fun calculateCooldownDays(): Int {
        val lastClaimTime = _uiState.value.userWallet.lastFreeClaimTime
        if (lastClaimTime == null) return 0

        val daysSinceLastClaim = (System.currentTimeMillis() - lastClaimTime) / (1000 * 60 * 60 * 24)
        return maxOf(0, 3 - daysSinceLastClaim.toInt())
    }

    /**
     * Select a store item to purchase
     */
    fun selectItem(item: StoreItem) {
        _uiState.update {
            it.copy(
                selectedItem = item,
                showPurchaseDialog = true
            )
        }
    }

    /**
     * Dismiss purchase dialog
     */
    fun dismissPurchaseDialog() {
        _uiState.update {
            it.copy(
                showPurchaseDialog = false,
                selectedItem = null,
                purchaseResult = null
            )
        }
    }

    /**
     * Purchase item with coins
     */
    fun purchaseWithCoins(item: StoreItem) {
        viewModelScope.launch {
            val currentCoins = _uiState.value.userWallet.coins

            if (currentCoins >= item.coinPrice) {
                // Successful purchase
                val newBalance = currentCoins - item.coinPrice
                _uiState.update {
                    it.copy(
                        userWallet = it.userWallet.copy(coins = newBalance),
                        purchaseResult = PurchaseResult.Success(item, newBalance)
                    )
                }

                // TODO: Save to Firebase/local storage
                // TODO: Add item to inventory

            } else {
                // Insufficient funds
                _uiState.update {
                    it.copy(
                        purchaseResult = PurchaseResult.InsufficientFunds(
                            required = item.coinPrice,
                            current = currentCoins
                        )
                    )
                }
            }
        }
    }

    /**
     * Claim free daily gift
     */
    fun claimFreeGift(item: StoreItem) {
        viewModelScope.launch {
            if (checkFreeGiftAvailability()) {
                _uiState.update {
                    it.copy(
                        userWallet = it.userWallet.copy(
                            lastFreeClaimTime = System.currentTimeMillis()
                        ),
                        canClaimFreeGift = false,
                        freeGiftCooldownDays = 3,
                        purchaseResult = PurchaseResult.Success(item, it.userWallet.coins)
                    )
                }
                // TODO: Add item to inventory
            } else {
                _uiState.update {
                    it.copy(purchaseResult = PurchaseResult.OnCooldown)
                }
            }
        }
    }

    /**
     * Watch ad to get reward
     */
    fun watchAdForReward(item: StoreItem) {
        viewModelScope.launch {
            // TODO: Implement ad watching logic with AdMob
            // Simulate ad completion
            delay(100)

            _uiState.update {
                it.copy(
                    userWallet = it.userWallet.copy(
                        lastAdWatchTime = System.currentTimeMillis(),
                        adsWatchedToday = it.userWallet.adsWatchedToday + 1
                    ),
                    purchaseResult = PurchaseResult.Success(item, it.userWallet.coins)
                )
            }
            // TODO: Add item to inventory
        }
    }

    /**
     * Purchase with real money
     */
    fun purchaseWithRealMoney(item: StoreItem) {
        viewModelScope.launch {
            // TODO: Implement in-app purchase logic
            _uiState.update {
                it.copy(
                    purchaseResult = PurchaseResult.Success(item, it.userWallet.coins)
                )
            }
            // TODO: Add item to inventory
        }
    }

    /**
     * Add coins to wallet (for testing or rewards)
     */
    fun addCoins(amount: Int) {
        _uiState.update {
            it.copy(
                userWallet = it.userWallet.copy(
                    coins = it.userWallet.coins + amount
                )
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clear purchase result
     */
    fun clearPurchaseResult() {
        _uiState.update { it.copy(purchaseResult = null) }
    }

    /**
     * Refresh store data
     */
    fun refreshStore() {
        loadStoreData()
    }
}
