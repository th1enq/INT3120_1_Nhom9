package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Firebase-backed ViewModel for Store Screen
 * Manages store items, purchases, and user wallet from Firestore
 */
class StoreViewModelFirebase(
    private val authRepository: FirebaseAuthRepository = FirebaseAuthRepository(),
    private val firestoreRepository: FirebaseFirestoreRepository = FirebaseFirestoreRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()
    
    private val TAG = "StoreViewModelFirebase"

    init {
        loadStoreData()
    }

    /**
     * Load store data including categories and user wallet
     */
    private fun loadStoreData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val currentUserId = authRepository.currentUser?.uid
                if (currentUserId == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "Not authenticated"
                        ) 
                    }
                    return@launch
                }
                
                // Load user wallet
                val wallet = loadUserWallet(currentUserId)
                
                // Check free gift availability
                val canClaimFree = checkFreeGiftAvailability(wallet)
                val cooldownDays = calculateCooldownDays(wallet)
                
                // Create store categories (static data)
                val categories = createStoreCategories()

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
                Log.e(TAG, "Error loading store data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to load store data: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load user wallet from Firestore
     */
    private suspend fun loadUserWallet(userId: String): UserWallet {
        return try {
            val result = firestoreRepository.getDocument(
                "user_wallets", 
                userId,
                FirebaseUserWallet::class.java
            )
            
            result.fold(
                onSuccess = { walletDoc ->
                    if (walletDoc != null) {
                        Log.d(TAG, "[STORE] Wallet loaded from user_wallets/$userId")
                        Log.d(TAG, "[STORE] Wallet has ${walletDoc.coins} coins")
                        UserWallet(
                            coins = walletDoc.coins,
                            lastFreeClaimTime = walletDoc.lastFreeGiftDate?.let { 
                                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                                    .atStartOfDay()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            }
                        )
                    } else {
                        Log.d(TAG, "[STORE] No wallet found at document: user_wallets/$userId")
                        Log.d(TAG, "[STORE] Creating default wallet with 1000 coins")
                        // Create default wallet with 1000 coins if not exists
                        val defaultWallet = FirebaseUserWallet(
                            id = userId,
                            userId = userId,
                            coins = 1000, // Starting coins
                            freeCoins = 0
                        )
                        firestoreRepository.setDocument("user_wallets", userId, defaultWallet)
                        
                        UserWallet(coins = 1000)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading wallet", e)
                    UserWallet(coins = 0)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading wallet", e)
            UserWallet(coins = 0)
        }
    }

    /**
     * Check if free gift can be claimed
     */
    private suspend fun checkFreeGiftAvailability(wallet: UserWallet): Boolean {
        return try {
            if (wallet.lastFreeClaimTime == null) {
                return true
            }
            
            val lastClaimDate = java.time.Instant.ofEpochMilli(wallet.lastFreeClaimTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            
            val today = LocalDate.now()
            val daysSince = java.time.temporal.ChronoUnit.DAYS.between(lastClaimDate, today)
            
            daysSince >= 3
        } catch (e: Exception) {
            Log.e(TAG, "Error checking free gift availability", e)
            false
        }
    }

    /**
     * Calculate cooldown days for free gift
     */
    private suspend fun calculateCooldownDays(wallet: UserWallet): Int {
        return try {
            if (wallet.lastFreeClaimTime == null) {
                return 0
            }
            
            val lastClaimDate = java.time.Instant.ofEpochMilli(wallet.lastFreeClaimTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            
            val today = LocalDate.now()
            val daysSince = java.time.temporal.ChronoUnit.DAYS.between(lastClaimDate, today).toInt()
            
            maxOf(0, 3 - daysSince)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cooldown", e)
            0
        }
    }

    /**
     * Create store categories with items (static data)
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
                        coinPrice = 50
                    ),
                    StoreItem(
                        id = "fertilizer_8h",
                        name = "8h Fertilizer",
                        description = "Speed up growth by 8 hours",
                        iconRes = R.drawable.phan8h,
                        type = StoreItemType.FERTILIZER,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 80
                    ),
                    StoreItem(
                        id = "fertilizer_24h",
                        name = "24h Fertilizer",
                        description = "Speed up growth by 24 hours",
                        iconRes = R.drawable.phan24h,
                        type = StoreItemType.FERTILIZER,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 100
                    )
                )
            ),

            // Care Items
            StoreCategory(
                id = "care_items",
                name = "Care Items",
                items = listOf(
                    StoreItem(
                        id = "watering_can",
                        name = "Watering Can",
                        description = "Instantly add 30% water",
                        iconRes = R.drawable.xit,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 30
                    ),
                    StoreItem(
                        id = "sunlight_bottle",
                        name = "Sunlight Bottle",
                        description = "Instantly add 30% sunlight",
                        iconRes = R.drawable.sun,
                        type = StoreItemType.TOOL,
                        purchaseType = PurchaseType.COIN,
                        coinPrice = 30
                    )
                )
            )
        )
    }

    /**
     * Purchase an item
     */
    fun purchaseItem(item: StoreItem) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.currentUser?.uid ?: return@launch
                val wallet = _uiState.value.userWallet
                
                when (item.purchaseType) {
                    PurchaseType.COIN -> {
                        if (wallet.coins < item.coinPrice) {
                            _uiState.update { 
                                it.copy(errorMessage = "Not enough coins") 
                            }
                            return@launch
                        }
                        
                        // Deduct coins
                        val newCoins = wallet.coins - item.coinPrice
                        firestoreRepository.updateDocument(
                            "user_wallets",
                            currentUserId,
                            mapOf("coins" to newCoins)
                        )
                        
                        // Add to inventory
                        addToInventory(currentUserId, item)
                        
                        // Record purchase
                        recordPurchase(currentUserId, item, "coin")
                        
                        // Update local state
                        _uiState.update { 
                            it.copy(
                                userWallet = wallet.copy(coins = newCoins),
                                purchaseResult = PurchaseResult.Success(item, newCoins),
                                errorMessage = null
                            ) 
                        }
                    }
                    
                    PurchaseType.FREE_DAILY -> {
                        if (!_uiState.value.canClaimFreeGift) {
                            _uiState.update { 
                                it.copy(
                                    errorMessage = "Free gift available in ${_uiState.value.freeGiftCooldownDays} days"
                                ) 
                            }
                            return@launch
                        }
                        
                        // Update last free gift date
                        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        firestoreRepository.updateDocument(
                            "user_wallets",
                            currentUserId,
                            mapOf("lastFreeGiftDate" to today)
                        )
                        
                        // Add to inventory
                        addToInventory(currentUserId, item)
                        
                        // Record purchase
                        recordPurchase(currentUserId, item, "free")
                        
                        // Update local state
                        _uiState.update { 
                            it.copy(
                                canClaimFreeGift = false,
                                freeGiftCooldownDays = 3,
                                purchaseResult = PurchaseResult.Success(item, wallet.coins),
                                errorMessage = null
                            ) 
                        }
                    }
                    
                    PurchaseType.WATCH_AD -> {
                        // TODO: Implement ad watching
                        // For now, just add to inventory
                        addToInventory(currentUserId, item)
                        recordPurchase(currentUserId, item, "ad")
                        
                        _uiState.update { 
                            it.copy(
                                purchaseResult = PurchaseResult.Success(item, wallet.coins),
                                errorMessage = null
                            ) 
                        }
                    }
                    
                    else -> {
                        _uiState.update { 
                            it.copy(errorMessage = "Purchase type not supported") 
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error purchasing item", e)
                _uiState.update { 
                    it.copy(errorMessage = "Purchase failed: ${e.message}") 
                }
            }
        }
    }

    /**
     * Add purchased item to user's garden inventory
     */
    private suspend fun addToInventory(userId: String, item: StoreItem) {
        try {
            val result = firestoreRepository.getDocument(
                "garden_inventories",
                userId,
                FirebaseGardenInventory::class.java
            )
            
            result.fold(
                onSuccess = { inventoryDoc ->
                    val updates = mutableMapOf<String, Any>()
                    
                    when (item.id) {
                        "seed_normal", "seed_rare", "seed_super_rare" -> {
                            val currentSeeds = inventoryDoc?.seeds ?: 0
                            updates["seeds"] = currentSeeds + 1
                        }
                        "fertilizer_4h" -> {
                            val current = inventoryDoc?.fertilizer4h ?: 0
                            updates["fertilizer4h"] = current + 1
                        }
                        "fertilizer_8h" -> {
                            val current = inventoryDoc?.fertilizer8h ?: 0
                            updates["fertilizer8h"] = current + 1
                        }
                        "fertilizer_12h" -> {
                            val current = inventoryDoc?.fertilizer12h ?: 0
                            updates["fertilizer12h"] = current + 1
                        }
                        "watering_can" -> {
                            val current = inventoryDoc?.wateringCan ?: 0
                            updates["wateringCan"] = current + 1
                        }
                        "sunlight_bottle" -> {
                            val current = inventoryDoc?.sunlightBottle ?: 0
                            updates["sunlightBottle"] = current + 1
                        }
                    }
                    
                    if (updates.isNotEmpty()) {
                        if (inventoryDoc == null) {
                            // Create new inventory
                            val newInventory = FirebaseGardenInventory(
                                id = userId,
                                userId = userId
                            )
                            firestoreRepository.setDocument("garden_inventories", userId, newInventory)
                        }
                        
                        firestoreRepository.updateDocument(
                            "garden_inventories",
                            userId,
                            updates
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading inventory", e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to inventory", e)
        }
    }

    /**
     * Record purchase in history
     */
    private suspend fun recordPurchase(userId: String, item: StoreItem, purchaseType: String) {
        try {
            val purchase = FirebasePurchaseHistory(
                userId = userId,
                itemId = item.id,
                itemName = item.name,
                itemType = item.type.name,
                price = item.coinPrice,
                purchaseType = purchaseType
            )
            
            firestoreRepository.addDocument("purchase_history", purchase)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording purchase", e)
        }
    }

    /**
     * Dismiss purchase result
     */
    fun dismissPurchaseResult() {
        _uiState.update { it.copy(purchaseResult = null) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Refresh store data
     */
    fun refresh() {
        loadStoreData()
    }
    
    /**
     * Select category
     */
    fun selectCategory(index: Int) {
        _uiState.update { it.copy(selectedCategory = index) }
    }
    
    /**
     * Select item for purchase
     */
    fun selectItem(item: StoreItem) {
        _uiState.update { 
            it.copy(
                selectedItem = item,
                showPurchaseDialog = true,
                purchaseQuantity = 1
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
                purchaseQuantity = 1
            ) 
        }
    }
    
    /**
     * Update purchase quantity
     */
    fun updatePurchaseQuantity(quantity: Int) {
        _uiState.update { it.copy(purchaseQuantity = quantity) }
    }
    
    /**
     * Purchase with coins
     */
    fun purchaseWithCoins(item: StoreItem, quantity: Int) {
        // For now, just purchase once (quantity support can be added later)
        purchaseItem(item)
    }
    
    /**
     * Claim free gift
     */
    fun claimFreeGift(item: StoreItem) {
        purchaseItem(item)
    }
    
    /**
     * Watch ad for reward
     */
    fun watchAdForReward(item: StoreItem) {
        purchaseItem(item)
    }
    
    /**
     * Purchase with real money (not implemented)
     */
    fun purchaseWithRealMoney(item: StoreItem) {
        _uiState.update { 
            it.copy(errorMessage = "Real money purchases not yet implemented") 
        }
    }
    
    /**
     * Clear purchase result
     */
    fun clearPurchaseResult() {
        _uiState.update { it.copy(purchaseResult = null) }
    }
}
