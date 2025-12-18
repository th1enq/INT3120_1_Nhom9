package com.example.coupleapp.ui.components.store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.StoreCategory
import com.example.coupleapp.data.model.StoreItem
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Shelves content with all categories
 */
@Composable
fun ShelvesContent(
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

/**
 * Category shelf section with title
 */
@Composable
fun CategoryShelfSection(
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

/**
 * Shelf row with items displayed on shelf image
 */
@Composable
fun ShelfRow(
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

/**
 * Individual shelf item with image and price button
 */
@Composable
fun ShelfItem(
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
    val isVietnamese = Locale.getDefault().language == "vi"
    val localizedName = item.getLocalizedName(isVietnamese)

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
                contentDescription = localizedName,
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

/**
 * Category divider with decorative line
 */
@Composable
fun CategoryDivider(categoryName: String) {
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
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
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
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF8B4513).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
