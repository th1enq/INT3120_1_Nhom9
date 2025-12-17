package com.example.coupleapp.data.model

import androidx.annotation.DrawableRes

/**
 * Store item types
 */
enum class StoreItemType {
    CARE_PACKAGE,    // Gói chăm sóc cây trồng
    SEED,            // Hạt giống
    FERTILIZER,      // Phân bón
    TOOL             // Dụng cụ trồng cây
}

/**
 * Purchase type for store items
 */
enum class PurchaseType {
    FREE_DAILY,      // Miễn phí mỗi ngày (3 ngày)
    WATCH_AD,        // Xem quảng cáo
    COIN,            // Mua bằng coin
    REAL_MONEY       // Mua bằng tiền thật
}

/**
 * Store item data
 */
data class StoreItem(
    val id: String,
    val name: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val type: StoreItemType,
    val purchaseType: PurchaseType,
    val coinPrice: Int = 0,
    val realPrice: Double = 0.0,
    val rarity: SeedRarity? = null,
    val durationHours: Int? = null,  // For fertilizers
    val isAvailable: Boolean = true,
    val cooldownDays: Int? = null    // For free items
)

/**
 * Store category for grouping items
 */
data class StoreCategory(
    val id: String,
    val name: String,
    val items: List<StoreItem>
)

/**
 * User inventory item
 */
data class InventoryItem(
    val itemId: String,
    val quantity: Int,
    val purchasedAt: Long? = null
)

/**
 * User wallet/balance
 */
data class UserWallet(
    val coins: Int = 0,
    val lastFreeClaimTime: Long? = null,
    val lastAdWatchTime: Long? = null,
    val adsWatchedToday: Int = 0
)

/**
 * Purchase result
 */
sealed class PurchaseResult {
    data class Success(val item: StoreItem, val newBalance: Int, val quantity: Int = 1) : PurchaseResult()
    data class InsufficientFunds(val required: Int, val current: Int) : PurchaseResult()
    object OnCooldown : PurchaseResult()
    object AdNotAvailable : PurchaseResult()
    data class Error(val message: String) : PurchaseResult()
}

/**
 * Store UI state
 */
data class StoreUiState(
    val isLoading: Boolean = true,
    val categories: List<StoreCategory> = emptyList(),
    val userWallet: UserWallet = UserWallet(),
    val selectedCategory: Int = 0,
    val errorMessage: String? = null,
    val showPurchaseDialog: Boolean = false,
    val selectedItem: StoreItem? = null,
    val purchaseQuantity: Int = 1,
    val purchaseResult: PurchaseResult? = null,
    val canClaimFreeGift: Boolean = false,
    val canWatchAd: Boolean = true,
    val freeGiftCooldownDays: Int = 0
)
