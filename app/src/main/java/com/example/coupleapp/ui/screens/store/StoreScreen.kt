package com.example.coupleapp.ui.screens.store

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.StoreUiState
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.store.*
import com.example.coupleapp.viewmodel.StoreViewModelFirebase
import kotlinx.coroutines.delay

@Composable
fun StoreScreen(
    onBackClick: () -> Unit,
    viewModel: StoreViewModelFirebase = viewModel(),
    questViewModel: com.example.coupleapp.viewmodel.QuestViewModelFirebase? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Track if we ever showed loading (to distinguish cache hit vs network load)
    var wasLoading by remember { mutableStateOf(uiState.isLoading) }
    var visible by remember { mutableStateOf(!uiState.isLoading) } // If cache hit, show immediately

    // Update wasLoading flag when loading starts
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            wasLoading = true
            visible = false
        } else {
            if (!visible) {
                // If we showed loading, use longer delay to avoid lag during transition
                // If cache hit (never showed loading), show immediately
                val transitionDelay = if (wasLoading) 150L else 0L
                if (transitionDelay > 0) delay(transitionDelay)
                visible = true
            }
        }
    }

    // Purchase dialog
    if (uiState.showPurchaseDialog && uiState.selectedItem != null) {
        PurchaseConfirmDialog(
            item = uiState.selectedItem!!,
            quantity = uiState.purchaseQuantity,
            userCoins = uiState.userWallet.coins,
            canClaimFree = uiState.canClaimFreeGift,
            cooldownDays = uiState.freeGiftCooldownDays,
            onDismiss = { viewModel.dismissPurchaseDialog() },
            onQuantityChange = { viewModel.updatePurchaseQuantity(it) },
            onPurchaseCoins = { item -> 
                viewModel.purchaseWithCoins(item, uiState.purchaseQuantity)
                // Update quest progress when purchasing care items (fertilizer, watering can, etc.)
                if (item.type == com.example.coupleapp.data.model.StoreItemType.FERTILIZER || 
                    item.type == com.example.coupleapp.data.model.StoreItemType.TOOL) {
                    questViewModel?.updateQuestProgress(com.example.coupleapp.data.model.QuestType.CARE_PLANT, 1)
                }
            },
            onClaimFree = { item -> 
                viewModel.claimFreeGift(item)
                questViewModel?.updateQuestProgress(com.example.coupleapp.data.model.QuestType.CARE_PLANT, 1)
            },
            onWatchAd = { viewModel.watchAdForReward(it) },
            onPurchaseReal = { viewModel.purchaseWithRealMoney(it) },
            isPurchasing = uiState.isPurchasing
        )
    }

    // Purchase result dialog
    uiState.purchaseResult?.let { result ->
        PurchaseResultDialog(
            result = result,
            onDismiss = {
                viewModel.clearPurchaseResult()
                viewModel.dismissPurchaseDialog()
            }
        )
    }

    // If cache hit (never showed loading), skip Crossfade entirely and show content directly
    // This avoids any transition lag when data is already cached
    if (!wasLoading && !uiState.isLoading) {
        // Direct content render for cache hit - no transition animation
        StoreContent(
            visible = visible,
            uiState = uiState,
            onBackClick = onBackClick,
            viewModel = viewModel
        )
    } else {
        // Normal flow with Crossfade for loading → content transition
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 250),  // Slightly longer for smoother transition
            label = "LoadingCrossfade"
        ) { loading ->
            if (loading) {
                LoadingScreen(message = stringResource(R.string.loading_store))
            } else {
                StoreContent(
                    visible = visible,
                    uiState = uiState,
                    onBackClick = onBackClick,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun StoreContent(
    visible: Boolean,
    uiState: StoreUiState,
    onBackClick: () -> Unit,
    viewModel: StoreViewModelFirebase
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image (store2.png) - full screen
        Image(
            painter = painterResource(id = R.drawable.store2),
            contentDescription = "Store background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Top bar with slide animation (like Friend page)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)) +
                    slideInVertically(animationSpec = tween(500)) { -it / 4 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            StoreTopBar(
                coins = uiState.userWallet.coins,
                onBackClick = onBackClick
            )
        }

        // Scrollable content with staggered animation (like Friend page)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) +
                    slideInVertically(animationSpec = tween(500, delayMillis = 150)) { it / 4 },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.BottomCenter)
        ) {
            ShelvesContent(
                categories = uiState.categories,
                onItemClick = { viewModel.selectItem(it) },
                userCoins = uiState.userWallet.coins,
                canClaimFree = uiState.canClaimFreeGift,
                cooldownDays = uiState.freeGiftCooldownDays
            )
        }
    }
}    
