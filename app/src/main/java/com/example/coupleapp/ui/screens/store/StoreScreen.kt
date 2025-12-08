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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.store.*
import com.example.coupleapp.viewmodel.StoreViewModel
import kotlinx.coroutines.delay

@Composable
fun StoreScreen(
    onBackClick: () -> Unit,
    viewModel: StoreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }

    // Animation timing like Friend page
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(200)  // Match Friend page delay
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            delay(200)
            visible = true
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
            onPurchaseCoins = { viewModel.purchaseWithCoins(it, uiState.purchaseQuantity) },
            onClaimFree = { viewModel.claimFreeGift(it) },
            onWatchAd = { viewModel.watchAdForReward(it) },
            onPurchaseReal = { viewModel.purchaseWithRealMoney(it) }
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

    Crossfade(
        targetState = uiState.isLoading,
        animationSpec = tween(durationMillis = 200),  // Fast like Missing/Quest
        label = "LoadingCrossfade"
    ) { loading ->
        if (loading) {
            LoadingScreen()
        } else {
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
    }
}    
