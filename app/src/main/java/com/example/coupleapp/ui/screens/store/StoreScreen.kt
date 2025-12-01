package com.example.coupleapp.ui.screens.store

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.viewmodel.StoreViewModel
import kotlinx.coroutines.delay

@Composable
fun StoreScreen(
    onBackClick: () -> Unit,
    viewModel: StoreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(100)
            visible = true
        }
    }

    // Purchase dialog
    if (uiState.showPurchaseDialog && uiState.selectedItem != null) {
        PurchaseConfirmDialog(
            item = uiState.selectedItem!!,
            userCoins = uiState.userWallet.coins,
            canClaimFree = uiState.canClaimFreeGift,
            cooldownDays = uiState.freeGiftCooldownDays,
            onDismiss = { viewModel.dismissPurchaseDialog() },
            onPurchaseCoins = { viewModel.purchaseWithCoins(it) },
            onClaimFree = { viewModel.claimFreeGift(it) },
            onWatchAd = { viewModel.watchAdForReward(it) },
            onPurchaseReal = { viewModel.purchaseWithRealMoney(it) }
        )
    }

    // Purchase result handling
    LaunchedEffect(uiState.purchaseResult) {
        uiState.purchaseResult?.let { result ->
            delay(1500)
            viewModel.clearPurchaseResult()
            viewModel.dismissPurchaseDialog()
        }
    }

    Crossfade(
        targetState = uiState.isLoading,
        animationSpec = tween(durationMillis = 400),
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

                // Top bar overlay
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                ) {
                    StoreTopBar(
                        coins = uiState.userWallet.coins,
                        onBackClick = onBackClick
                    )
                }

                // Scrollable content directly on background (no rounded container)
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(500)
                    ) + fadeIn(tween(400)),
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

@Composable
private fun ShelvesContent(
    categories: List<StoreCategory>,
    onItemClick: (StoreItem) -> Unit,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Display all categories with shelves
        categories.forEachIndexed { index, category ->
            CategoryShelfSection(
                title = category.name,
                categoryId = category.id,
                items = category.items,
                onItemClick = onItemClick,
                userCoins = userCoins,
                canClaimFree = canClaimFree,
                cooldownDays = cooldownDays
            )
            
            // Add divider between categories (except after last one)
            if (index < categories.size - 1) {
                CategoryDivider(category.name)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryShelfSection(
    title: String,
    categoryId: String,
    items: List<StoreItem>,
    onItemClick: (StoreItem) -> Unit,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    Column {
        // Section title
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8B4513),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Items in rows with shelves
        ShelfRow(
            categoryId = categoryId,
            items = items,
            onItemClick = onItemClick,
            userCoins = userCoins,
            canClaimFree = canClaimFree,
            cooldownDays = cooldownDays
        )
    }
}

@Composable
private fun ShelfRow(
    categoryId: String,
    items: List<StoreItem>,
    onItemClick: (StoreItem) -> Unit,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    // Smaller size for seeds and fertilizers
    val isSmaller = categoryId == "seeds" || categoryId == "fertilizers"
    
    Column {
        // Display items in rows of 3 with shelf background
        items.chunked(3).forEach { rowItems ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isSmaller) 145.dp else 160.dp)
                    .padding(vertical = 4.dp)
            ) {
                // Shelf background image (ke.png)
                Image(
                    painter = painterResource(id = R.drawable.ke),
                    contentDescription = "Shelf",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .offset(x = 15.dp),
                    contentScale = ContentScale.FillBounds
                )
                
                // Items placed on shelf
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    rowItems.forEach { item ->
                        ShelfItem(
                            item = item,
                            isSmaller = isSmaller,
                            onClick = { onItemClick(item) },
                            userCoins = userCoins,
                            canClaimFree = canClaimFree,
                            cooldownDays = cooldownDays
                        )
                    }
                    
                    // Add empty spaces if row has less than 3 items
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfItem(
    item: StoreItem,
    isSmaller: Boolean = false,
    onClick: () -> Unit,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )
    
    val itemSize = if (isSmaller) 82.dp else 100.dp
    val itemWidth = if (isSmaller) 105.dp else 115.dp
    val spacing = if (isSmaller) 3.dp else 6.dp

    Column(
        modifier = Modifier
            .width(itemWidth)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Item image
        Box(
            modifier = Modifier.size(itemSize),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Rarity/Duration badge
            item.rarity?.let { rarity ->
                RarityBadge(
                    rarity = rarity,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            item.durationHours?.let { hours ->
                DurationBadge(
                    hours = hours,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing))

        // Price button
        PriceButton(
            item = item,
            userCoins = userCoins,
            canClaimFree = canClaimFree,
            cooldownDays = cooldownDays
        )
    }
}

@Composable
private fun StoreTopBar(
    coins: Int,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button on the left
        BackButton(onClick = onBackClick)

        Spacer(modifier = Modifier.weight(1f))

        // Only Coin display on the right (removed gem display)
        CoinDisplay(
            icon = null,
            value = coins,
            backgroundColor = listOf(Color(0xFFFFE082), Color(0xFFFFD54F), Color(0xFFFFCA28)),
            borderColor = Color(0xFFFFB300)
        )
    }
}

@Composable
private fun CoinDisplay(
    icon: Int? = null,
    value: Int,
    backgroundColor: List<Color>,
    borderColor: Color
) {
    Row(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(colors = backgroundColor))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            // Use image icon (for gems/diamonds)
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // Use canvas for coin
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                    )
                )
                drawCircle(
                    color = Color(0xFFB8860B),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Text(
            text = "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        // Plus button
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
                .clickable { /* TODO: Add coins purchase */ },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CategoryDivider(categoryName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF8B4513).copy(alpha = 0.5f)
                        )
                    )
                )
        )
        
        Text(
            text = "━━━━",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8B4513).copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF8B4513).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(40.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFFF5252))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RarityBadge(
    rarity: SeedRarity,
    modifier: Modifier = Modifier
) {
    val (color, bgColor) = when (rarity) {
        SeedRarity.NORMAL -> Color(0xFF4CAF50) to Color(0xFFC8E6C9)
        SeedRarity.RARE -> Color(0xFF2196F3) to Color(0xFFBBDEFB)
        SeedRarity.SUPER_RARE -> Color(0xFF9C27B0) to Color(0xFFE1BEE7)
    }

    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-6).dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (rarity) {
                SeedRarity.NORMAL -> "N"
                SeedRarity.RARE -> "R"
                SeedRarity.SUPER_RARE -> "S"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DurationBadge(
    hours: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-6).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2196F3))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${hours}h",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun PriceButton(
    item: StoreItem,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    val (buttonColor, textColor, text, iconType) = when (item.purchaseType) {
        PurchaseType.FREE_DAILY -> {
            if (canClaimFree) {
                Quadruple(
                    Brush.horizontalGradient(listOf(Color(0xFF81C784), Color(0xFF66BB6A))),
                    Color.White,
                    "Claim",
                    "FREE"
                )
            } else {
                Quadruple(
                    Brush.horizontalGradient(listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD))),
                    Color.White,
                    "~${cooldownDays}d",
                    "TIME"
                )
            }
        }
        PurchaseType.WATCH_AD -> Quadruple(
            Brush.horizontalGradient(listOf(Color(0xFFFF6F00), Color(0xFFFF8F00))),
            Color.White,
            "Watch",
            "AD"
        )
        PurchaseType.COIN -> {
            val canAfford = userCoins >= item.coinPrice
            Quadruple(
                if (canAfford)
                    Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFCA28)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD))),
                Color.White,
                "${item.coinPrice}",
                "COIN"
            )
        }
        PurchaseType.REAL_MONEY -> Quadruple(
            Brush.horizontalGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))),
            Color.White,
            "$${String.format("%.0f", item.realPrice / 1000)}k",
            "MONEY"
        )
    }

    Box(
        modifier = Modifier
            .width(90.dp)
            .height(36.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(buttonColor)
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Icon based on type
            when (iconType) {
                "COIN" -> {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                            )
                        )
                        drawCircle(
                            color = Color(0xFFB8860B),
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                "AD" -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                "FREE" -> {
                    // Sparkle or star icon could go here
                }
                "MONEY" -> {
                    // Money/dollar icon
                }
            }

            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

// Helper data class for quadruple values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun PurchaseConfirmDialog(
    item: StoreItem,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int,
    onDismiss: () -> Unit,
    onPurchaseCoins: (StoreItem) -> Unit,
    onClaimFree: (StoreItem) -> Unit,
    onWatchAd: (StoreItem) -> Unit,
    onPurchaseReal: (StoreItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFF8E1),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF5D4037)
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.description,
                    fontSize = 14.sp,
                    color = Color(0xFF8D6E63),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (item.purchaseType) {
                    PurchaseType.FREE_DAILY -> {
                        if (canClaimFree) {
                            Text(
                                text = "🎁 Claim for Free!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Text(
                                text = "⏳ Available in ${cooldownDays} Days",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9E9E9E)
                            )
                        }
                    }
                    PurchaseType.WATCH_AD -> {
                        Text(
                            text = "📺 Watch Ad to Claim",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9C27B0)
                        )
                    }
                    PurchaseType.COIN -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Price: ",
                                fontSize = 14.sp,
                                color = Color(0xFF8D6E63)
                            )
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.coinPrice}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF5D4037)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Balance: ${userCoins} Coins",
                            fontSize = 12.sp,
                            color = if (userCoins >= item.coinPrice) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                    PurchaseType.REAL_MONEY -> {
                        Text(
                            text = "💎 Price: $$${String.format("%.2f", item.realPrice / 1000)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E88E5)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val enabled = when (item.purchaseType) {
                PurchaseType.FREE_DAILY -> canClaimFree
                PurchaseType.WATCH_AD -> true
                PurchaseType.COIN -> userCoins >= item.coinPrice
                PurchaseType.REAL_MONEY -> true
            }

            Button(
                onClick = {
                    when (item.purchaseType) {
                        PurchaseType.FREE_DAILY -> onClaimFree(item)
                        PurchaseType.WATCH_AD -> onWatchAd(item)
                        PurchaseType.COIN -> onPurchaseCoins(item)
                        PurchaseType.REAL_MONEY -> onPurchaseReal(item)
                    }
                },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFFBDBDBD)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when (item.purchaseType) {
                        PurchaseType.FREE_DAILY -> "Claim"
                        PurchaseType.WATCH_AD -> "Watch Ad"
                        PurchaseType.COIN -> "Buy"
                        PurchaseType.REAL_MONEY -> "Purchase"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF8D6E63)
                )
            }
        }
    )
}
